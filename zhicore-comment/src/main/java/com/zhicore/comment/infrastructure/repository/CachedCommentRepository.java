package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.comment.infrastructure.cursor.HotCursorCodec.HotCursor;
import com.zhicore.comment.infrastructure.cursor.TimeCursorCodec.TimeCursor;
import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 评论缓存仓储装饰器
 * 
 * 实现 Cache-Aside 模式：
 * - 读取：先查缓存，未命中再查数据库，然后回填缓存
 * - 写入：先更新数据库，再删除缓存
 * 
 * 缓存击穿防护：
 * - 热点数据识别：基于访问频率自动识别热点评论
 * - 分布式锁：对热点数据使用 Redisson 分布式锁防止并发查询数据库
 * - 双重检查锁（DCL）：获取锁后再次检查缓存
 * - 超时降级：锁获取超时时直接查询数据库
 * - 空值缓存：缓存空值防止缓存穿透
 *
 * @author ZhiCore Team
 */
@Slf4j
@Primary
@Repository
public class CachedCommentRepository implements CommentRepository {

    private final CommentRepository delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;
    private final HotDataIdentifier hotDataIdentifier;
    private final ObjectMapper objectMapper;

    public CachedCommentRepository(
            @Qualifier("commentRepositoryImpl") CommentRepository delegate,
            RedisTemplate<String, Object> redisTemplate,
            RedissonClient redissonClient,
            CacheProperties cacheProperties,
            HotDataIdentifier hotDataIdentifier,
            ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.cacheProperties = cacheProperties;
        this.hotDataIdentifier = hotDataIdentifier;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取分布式锁
     * 根据配置选择公平锁或非公平锁
     * 
     * @param lockKey 锁键
     * @return Redisson 锁对象
     */
    private RLock getLock(String lockKey) {
        if (cacheProperties.getLock().isFair()) {
            log.debug("Using fair lock for key: {}", lockKey);
            return redissonClient.getFairLock(lockKey);
        } else {
            log.debug("Using non-fair lock for key: {}", lockKey);
            return redissonClient.getLock(lockKey);
        }
    }

    /**
     * 获取锁的等待队列长度
     * 用于监控锁的竞争情况
     * 
     * 注意：Redisson RLock 接口不直接提供 getQueueLength() 方法
     * 此方法仅用于接口兼容性，实际返回 -1 表示不支持
     * 
     * @param lockKey 锁键
     * @return 等待队列长度，-1 表示不支持或获取失败
     */
    public int getLockQueueLength(String lockKey) {
        log.debug("Lock queue length monitoring not supported for key: {}", lockKey);
        return -1;
    }

    @Override
    public Optional<Comment> findById(Long id) {
        String cacheKey = CommentRedisKeys.detail(id);
        
        // Step 1: 第一次检查缓存
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (CacheConstants.NULL_VALUE.equals(cached)) {
                    // 空值缓存，防止缓存穿透
                    return Optional.empty();
                }
                Comment comment = objectMapper.convertValue(cached, Comment.class);
                return Optional.of(comment);
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis 连接失败，降级直接查询数据库
            log.error("Redis connection failed, falling back to database: commentId={}, error={}", 
                    id, e.getMessage());
            return delegate.findById(id);
        } catch (Exception e) {
            // 其他异常，降级直接查询数据库
            log.error("Cache read failed, falling back to database: commentId={}, error={}", 
                    id, e.getMessage(), e);
            return delegate.findById(id);
        }

        // 记录访问用于热点数据识别
        try {
            hotDataIdentifier.recordAccess("comment", id);
        } catch (Exception e) {
            log.warn("Failed to record access for hot data identification: commentId={}, error={}", 
                    id, e.getMessage());
        }

        // 检查是否为热点数据
        boolean isHotData = false;
        try {
            isHotData = hotDataIdentifier.isHotData("comment", id);
        } catch (Exception e) {
            log.warn("Failed to check hot data status: commentId={}, error={}", id, e.getMessage());
        }
        
        if (!isHotData) {
            // 非热点数据：直接查询数据库并缓存
            return loadAndCacheComment(id, cacheKey);
        }

        // 热点数据：使用分布式锁防止缓存击穿
        return loadCommentWithLock(id, cacheKey);
    }

    /**
     * 使用分布式锁加载评论数据（热点数据）
     */
    private Optional<Comment> loadCommentWithLock(Long commentId, String cacheKey) {
        String lockKey = CommentRedisKeys.lockDetail(commentId);
        RLock lock = getLock(lockKey);

        try {
            // Step 2: 尝试获取分布式锁（等待5秒，持有10秒）
            boolean acquired = lock.tryLock(
                    cacheProperties.getLock().getWaitTime(),
                    cacheProperties.getLock().getLeaseTime(),
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                // Step 6: 超时降级 - 直接查询数据库
                log.warn("Failed to acquire lock for comment {} within timeout, falling back to database", commentId);
                return delegate.findById(commentId);
            }

            try {
                // Step 3: DCL 双重检查 - 获取锁后再次检查缓存
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CacheConstants.NULL_VALUE.equals(cached)) {
                        return Optional.empty();
                    }
                    Comment comment = objectMapper.convertValue(cached, Comment.class);
                    return Optional.of(comment);
                }

                // Step 4: 查询数据库
                Optional<Comment> result = delegate.findById(commentId);

                // Step 5: 写入缓存
                try {
                    if (result.isPresent()) {
                        // Step 5a: 缓存数据（TTL + 随机抖动）
                        long ttl = cacheProperties.getTtl().getEntityDetail();
                        ttl = ttl + (long)(Math.random() * ttl * 0.1);
                        redisTemplate.opsForValue().set(cacheKey, result.get(), ttl, TimeUnit.SECONDS);
                    } else {
                        // Step 5b: 缓存空值（60秒 TTL）
                        redisTemplate.opsForValue().set(
                                cacheKey,
                                CacheConstants.NULL_VALUE,
                                cacheProperties.getTtl().getNullValue(),
                                TimeUnit.SECONDS
                        );
                    }
                } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
                    log.error("Redis connection failed during cache write: commentId={}, error={}", 
                            commentId, e.getMessage());
                    // 缓存写入失败不影响业务
                } catch (Exception e) {
                    log.warn("Failed to cache comment after database query: commentId={}, error={}", 
                            commentId, e.getMessage());
                    // 缓存写入失败不影响业务
                }

                return result;

            } finally {
                // 确保锁被释放
                if (lock.isHeldByCurrentThread()) {
                    try {
                        lock.unlock();
                    } catch (Exception e) {
                        log.error("Failed to release lock for comment {}, will rely on auto-expiration: {}", 
                                commentId, e.getMessage());
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for comment {}, falling back to database", commentId);
            return delegate.findById(commentId);
        } catch (org.redisson.client.RedisException e) {
            // Redisson 连接异常，降级查询数据库
            log.error("Redisson connection failed for commentId={}, falling back to database: {}", 
                    commentId, e.getMessage());
            return delegate.findById(commentId);
        } catch (Exception e) {
            log.error("Unexpected error during cache operation for comment {}: {}", commentId, e.getMessage(), e);
            // 发生异常时降级查询数据库
            return delegate.findById(commentId);
        }
    }

    /**
     * 加载并缓存评论数据（非热点数据）
     */
    private Optional<Comment> loadAndCacheComment(Long commentId, String cacheKey) {
        Optional<Comment> result = delegate.findById(commentId);

        // 回填缓存
        try {
            if (result.isPresent()) {
                long ttl = cacheProperties.getTtl().getEntityDetail();
                // 添加随机抖动防止缓存雪崩 (10% jitter)
                ttl = ttl + (long)(Math.random() * ttl * 0.1);
                redisTemplate.opsForValue().set(cacheKey, result.get(), ttl, TimeUnit.SECONDS);
            } else {
                // 缓存空值防止缓存穿透
                redisTemplate.opsForValue().set(
                        cacheKey,
                        CacheConstants.NULL_VALUE,
                        cacheProperties.getTtl().getNullValue(),
                        TimeUnit.SECONDS
                );
            }
        } catch (Exception e) {
            log.warn("Cache write failed for comment {}: {}", commentId, e.getMessage());
        }

        return result;
    }

    @Override
    public List<Comment> findByIds(java.util.Set<Long> ids) {
        // 批量查询直接委托给底层实现
        // 不进行缓存处理，避免复杂的批量缓存逻辑
        return delegate.findByIds(ids);
    }

    @Override
    public void save(Comment comment) {
        delegate.save(comment);
        // 新增不需要删除缓存
    }

    @Override
    public void update(Comment comment) {
        delegate.update(comment);
        // 删除缓存
        invalidateCache(comment.getId());
    }

    @Override
    public void delete(Long id) {
        delegate.delete(id);
        // 删除缓存
        invalidateCache(id);
    }

    // ========== 顶级评论查询 - 传统分页 ==========

    @Override
    public PageResult<Comment> findTopLevelByPostIdOrderByTimePage(Long postId, int page, int size) {
        // 列表查询不缓存，直接查数据库
        return delegate.findTopLevelByPostIdOrderByTimePage(postId, page, size);
    }

    @Override
    public PageResult<Comment> findTopLevelByPostIdOrderByLikesPage(Long postId, int page, int size) {
        return delegate.findTopLevelByPostIdOrderByLikesPage(postId, page, size);
    }

    // ========== 顶级评论查询 - 游标分页 ==========

    @Override
    public List<Comment> findTopLevelByPostIdOrderByTimeCursor(Long postId, TimeCursor cursor, int size) {
        return delegate.findTopLevelByPostIdOrderByTimeCursor(postId, cursor, size);
    }

    @Override
    public List<Comment> findTopLevelByPostIdOrderByLikesCursor(Long postId, HotCursor cursor, int size) {
        return delegate.findTopLevelByPostIdOrderByLikesCursor(postId, cursor, size);
    }

    // ========== 回复列表查询 ==========

    @Override
    public PageResult<Comment> findRepliesByRootIdPage(Long rootId, int page, int size) {
        return delegate.findRepliesByRootIdPage(rootId, page, size);
    }

    @Override
    public List<Comment> findRepliesByRootIdCursor(Long rootId, TimeCursor cursor, int size) {
        return delegate.findRepliesByRootIdCursor(rootId, cursor, size);
    }

    @Override
    public List<Comment> findHotRepliesByRootId(Long rootId, int limit) {
        return delegate.findHotRepliesByRootId(rootId, limit);
    }

    // ========== 统计查询 ==========

    @Override
    public long countTopLevelByPostId(Long postId) {
        String cacheKey = CommentRedisKeys.postCommentCount(postId);
        
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }
        } catch (Exception e) {
            log.warn("Cache read failed for post comment count {}: {}", postId, e.getMessage());
        }

        long count = delegate.countTopLevelByPostId(postId);

        try {
            redisTemplate.opsForValue().set(cacheKey, count);
        } catch (Exception e) {
            log.warn("Cache write failed for post comment count {}: {}", postId, e.getMessage());
        }

        return count;
    }

    @Override
    public int countRepliesByRootId(Long rootId) {
        String cacheKey = CommentRedisKeys.replyCount(rootId);
        
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Integer.parseInt(cached.toString());
            }
        } catch (Exception e) {
            log.warn("Cache read failed for reply count {}: {}", rootId, e.getMessage());
        }

        int count = delegate.countRepliesByRootId(rootId);

        try {
            redisTemplate.opsForValue().set(cacheKey, count);
        } catch (Exception e) {
            log.warn("Cache write failed for reply count {}: {}", rootId, e.getMessage());
        }

        return count;
    }

    @Override
    public Map<Long, CommentStats> batchGetStats(List<Long> commentIds) {
        // 统计数据直接查数据库，因为点赞数等会频繁变化
        // 实际的点赞数通过 Redis INCR/DECR 维护
        return delegate.batchGetStats(commentIds);
    }

    // ========== 管理员查询 ==========

    @Override
    public List<Comment> findByConditions(String keyword, Long postId, Long userId, int offset, int limit) {
        // 管理员查询不缓存，直接查数据库
        return delegate.findByConditions(keyword, postId, userId, offset, limit);
    }

    @Override
    public long countByConditions(String keyword, Long postId, Long userId) {
        // 管理员查询不缓存，直接查数据库
        return delegate.countByConditions(keyword, postId, userId);
    }

    // ========== 私有方法 ==========

    private void invalidateCache(Long commentId) {
        try {
            redisTemplate.delete(CommentRedisKeys.detail(commentId));
        } catch (Exception e) {
            log.warn("Cache invalidation failed for comment {}: {}", commentId, e.getMessage());
        }
    }

    /**
     * 批量查询评论（带缓存优化）
     * 
     * 优化策略：
     * 1. 批量查询缓存
     * 2. 区分热点数据和非热点数据
     * 3. 热点数据使用分布式锁（按ID排序避免死锁）
     * 4. 非热点数据直接批量查询数据库
     * 
     * @param commentIds 评论ID集合
     * @return 评论列表
     */
    public List<Comment> findByIdsWithCache(java.util.Set<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<Comment> result = new java.util.ArrayList<>();
        java.util.Set<Long> cacheMissIds = new java.util.HashSet<>();
        java.util.Set<Long> hotDataIds = new java.util.HashSet<>();
        java.util.Set<Long> nonHotDataIds = new java.util.HashSet<>();

        // Step 1: 批量查询缓存
        for (Long commentId : commentIds) {
            String cacheKey = CommentRedisKeys.detail(commentId);
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (!CacheConstants.NULL_VALUE.equals(cached)) {
                        Comment comment = objectMapper.convertValue(cached, Comment.class);
                        result.add(comment);
                    }
                    // 空值缓存或已缓存，跳过
                } else {
                    cacheMissIds.add(commentId);
                }
            } catch (Exception e) {
                log.warn("Cache lookup failed for commentId={}: {}", commentId, e.getMessage());
                cacheMissIds.add(commentId);
            }
        }

        if (cacheMissIds.isEmpty()) {
            log.debug("Batch query: all {} comments found in cache", commentIds.size());
            return result;
        }

        log.debug("Batch query: {} cache hits, {} cache misses", result.size(), cacheMissIds.size());

        // Step 2: 区分热点数据和非热点数据
        for (Long commentId : cacheMissIds) {
            hotDataIdentifier.recordAccess("comment", commentId);
            boolean isHot = hotDataIdentifier.isHotData("comment", commentId) 
                    || hotDataIdentifier.isManuallyMarkedAsHot("comment", commentId);
            
            if (isHot) {
                hotDataIds.add(commentId);
            } else {
                nonHotDataIds.add(commentId);
            }
        }

        log.debug("Batch query: {} hot data, {} non-hot data", hotDataIds.size(), nonHotDataIds.size());

        // Step 3: 非热点数据直接批量查询数据库
        if (!nonHotDataIds.isEmpty()) {
            try {
                // 批量查询数据库
                List<Comment> comments = delegate.findByIds(nonHotDataIds);
                result.addAll(comments);
                
                // 记录已查询到的评论ID
                java.util.Set<Long> foundIds = new java.util.HashSet<>();
                for (Comment comment : comments) {
                    foundIds.add(comment.getId());
                    
                    // 缓存评论
                    try {
                        String cacheKey = CommentRedisKeys.detail(comment.getId());
                        long ttl = cacheProperties.getTtl().getEntityDetail();
                        ttl = ttl + (long)(Math.random() * ttl * 0.1);
                        redisTemplate.opsForValue().set(cacheKey, comment, ttl, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("Failed to cache comment {}: {}", comment.getId(), e.getMessage());
                    }
                }
                
                // 缓存空值（未查询到的评论）
                for (Long commentId : nonHotDataIds) {
                    if (!foundIds.contains(commentId)) {
                        try {
                            String cacheKey = CommentRedisKeys.detail(commentId);
                            redisTemplate.opsForValue().set(
                                    cacheKey,
                                    CacheConstants.NULL_VALUE,
                                    cacheProperties.getTtl().getNullValue(),
                                    TimeUnit.SECONDS
                            );
                        } catch (Exception e) {
                            log.warn("Failed to cache null value for comment {}: {}", commentId, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to batch query non-hot comments: {}", e.getMessage(), e);
            }
        }

        // Step 4: 热点数据使用分布式锁（按ID排序避免死锁）
        if (!hotDataIds.isEmpty()) {
            List<Long> sortedHotIds = new java.util.ArrayList<>(hotDataIds);
            java.util.Collections.sort(sortedHotIds);
            
            for (Long commentId : sortedHotIds) {
                try {
                    Optional<Comment> commentOpt = findById(commentId);
                    commentOpt.ifPresent(result::add);
                } catch (Exception e) {
                    log.error("Failed to query hot comment {}: {}", commentId, e.getMessage(), e);
                }
            }
        }

        log.debug("Batch query completed: {} comments found", result.size());
        return result;
    }
}
