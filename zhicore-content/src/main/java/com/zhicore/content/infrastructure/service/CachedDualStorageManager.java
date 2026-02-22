package com.zhicore.content.infrastructure.service;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.service.DualStorageManager;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 带缓存的双存储管理器
 * 
 * 使用装饰器模式包装 DualStorageManagerImpl，添加缓存功能
 * 实现 Cache-Aside 模式：
 * - 读：先查缓存，未命中再查数据库，然后写缓存
 * - 写：先更新数据库，再删除缓存
 * 
 * 缓存策略：
 * 1. 文章内容缓存（热点数据）- TTL: 10分钟 + 随机抖动
 * 2. 文章完整详情缓存 - TTL: 10分钟 + 随机抖动
 * 3. 空值缓存防止缓存穿透 - TTL: 1分钟
 *
 * @author ZhiCore Team
 */
@Slf4j
@Primary
@Service
public class CachedDualStorageManager implements DualStorageManager {

    private final DualStorageManager delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final HotDataIdentifier hotDataIdentifier;
    private final CacheProperties cacheProperties;

    /**
     * 实体类型常量
     */
    private static final String ENTITY_TYPE_POST = "post";

    public CachedDualStorageManager(
            RedisTemplate<String, Object> redisTemplate,
            RedissonClient redissonClient,
            HotDataIdentifier hotDataIdentifier,
            CacheProperties cacheProperties,
            @Qualifier("dualStorageManagerImpl") DualStorageManager delegate) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.hotDataIdentifier = hotDataIdentifier;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public Long createPost(Post post, PostContent content) {
        // 创建文章，不缓存（等待第一次查询时缓存）
        Long postId = delegate.createPost(post, content);
        log.debug("Created post: {}, cache will be populated on first read", postId);
        return postId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PostDetail getPostFullDetail(Long postId) {
        String cacheKey = PostRedisKeys.fullDetail(postId);

        try {
            // Step 1: 第一次检查缓存
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            // Step 2: 命中缓存
            if (cached != null) {
                if (CacheConstants.NULL_VALUE.equals(cached)) {
                    log.debug("Cache hit (null value) for full detail: key={}", cacheKey);
                    return null;
                }
                log.debug("Cache hit for full detail: key={}", cacheKey);
                return (PostDetail) cached;
            }

            // Step 3: 未命中，记录访问并检查是否为热点数据
            log.debug("Cache miss for full detail: key={}", cacheKey);
            hotDataIdentifier.recordAccess(ENTITY_TYPE_POST, postId);

            // 检查是否为热点数据（自动识别或手动标记）
            boolean isHot = hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId) 
                    || hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId);

            if (isHot) {
                // 热点数据：使用分布式锁防止缓存击穿
                log.debug("Hot data detected for post: {}, using distributed lock", postId);
                return loadFullDetailWithLock(postId, cacheKey);
            } else {
                // 非热点数据：直接查询数据库
                log.debug("Non-hot data for post: {}, loading directly", postId);
                PostDetail detail = delegate.getPostFullDetail(postId);
                cacheFullDetail(cacheKey, detail);
                return detail;
            }

        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis 连接失败，降级直接查询数据库
            log.error("Redis connection failed for full detail, falling back to database: postId={}, error={}", 
                    postId, e.getMessage());
            return delegate.getPostFullDetail(postId);
        } catch (Exception e) {
            // 其他异常，降级直接查询数据库
            log.error("Cache operation failed for full detail, falling back to database: postId={}, error={}", 
                    postId, e.getMessage(), e);
            return delegate.getPostFullDetail(postId);
        }
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

    /**
     * 使用分布式锁加载文章完整详情
     * 
     * @param postId 文章ID
     * @param cacheKey 缓存键
     * @return 文章完整详情
     */
    private PostDetail loadFullDetailWithLock(Long postId, String cacheKey) {
        String lockKey = PostRedisKeys.lockFullDetail(postId);
        RLock lock = getLock(lockKey);

        try {
            // Step 2: 尝试获取分布式锁
            boolean acquired = lock.tryLock(
                    cacheProperties.getLock().getWaitTime(),
                    cacheProperties.getLock().getLeaseTime(),
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                // Step 6: 超时降级 - 直接查询数据库
                log.warn("Failed to acquire lock within timeout for post: {}, falling back to database", postId);
                return delegate.getPostFullDetail(postId);
            }

            try {
                // Step 3: DCL 双重检查 - 获取锁后再次检查缓存
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CacheConstants.NULL_VALUE.equals(cached)) {
                        log.debug("DCL: Cache hit (null value) after acquiring lock for post: {}", postId);
                        return null;
                    }
                    log.debug("DCL: Cache hit after acquiring lock for post: {}", postId);
                    return (PostDetail) cached;
                }

                // Step 4: 查询数据库
                log.debug("Loading full detail from database for post: {}", postId);
                PostDetail detail = delegate.getPostFullDetail(postId);

                // Step 5: 写入缓存
                cacheFullDetail(cacheKey, detail);

                return detail;

            } finally {
                // 确保锁被释放
                if (lock.isHeldByCurrentThread()) {
                    try {
                        lock.unlock();
                        log.debug("Released lock for post: {}", postId);
                    } catch (Exception e) {
                        log.error("Failed to release lock for post: {}, will rely on auto-expiration", postId, e);
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for post: {}, falling back to database", postId);
            return delegate.getPostFullDetail(postId);
        } catch (org.redisson.client.RedisException e) {
            // Redisson 连接异常，降级查询数据库
            log.error("Redisson connection failed for postId={}, falling back to database: {}", 
                    postId, e.getMessage());
            return delegate.getPostFullDetail(postId);
        } catch (Exception e) {
            log.error("Unexpected error during lock operation for post: {}", postId, e);
            return delegate.getPostFullDetail(postId);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PostContent getPostContent(Long postId) {
        String cacheKey = PostRedisKeys.content(postId);

        try {
            // Step 1: 第一次检查缓存
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            // Step 2: 命中缓存
            if (cached != null) {
                if (CacheConstants.NULL_VALUE.equals(cached)) {
                    log.debug("Cache hit (null value) for content: key={}", cacheKey);
                    return null;
                }
                log.debug("Cache hit for content: key={}", cacheKey);
                return (PostContent) cached;
            }

            // Step 3: 未命中，记录访问并检查是否为热点数据
            log.debug("Cache miss for content: key={}", cacheKey);
            hotDataIdentifier.recordAccess(ENTITY_TYPE_POST, postId);

            // 检查是否为热点数据（自动识别或手动标记）
            boolean isHot = hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId) 
                    || hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId);

            if (isHot) {
                // 热点数据：使用分布式锁防止缓存击穿
                log.debug("Hot data detected for post: {}, using distributed lock", postId);
                return loadContentWithLock(postId, cacheKey);
            } else {
                // 非热点数据：直接查询数据库
                log.debug("Non-hot data for post: {}, loading directly", postId);
                PostContent content = delegate.getPostContent(postId);
                cacheContent(cacheKey, content);
                return content;
            }

        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis 连接失败，降级直接查询数据库
            log.error("Redis connection failed for content, falling back to database: postId={}, error={}", 
                    postId, e.getMessage());
            return delegate.getPostContent(postId);
        } catch (Exception e) {
            // 其他异常，降级直接查询数据库
            log.error("Cache operation failed for content, falling back to database: postId={}, error={}", 
                    postId, e.getMessage(), e);
            return delegate.getPostContent(postId);
        }
    }

    /**
     * 使用分布式锁加载文章内容
     * 
     * @param postId 文章ID
     * @param cacheKey 缓存键
     * @return 文章内容
     */
    private PostContent loadContentWithLock(Long postId, String cacheKey) {
        String lockKey = PostRedisKeys.lockContent(postId);
        RLock lock = getLock(lockKey);

        try {
            // Step 2: 尝试获取分布式锁
            boolean acquired = lock.tryLock(
                    cacheProperties.getLock().getWaitTime(),
                    cacheProperties.getLock().getLeaseTime(),
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                // Step 6: 超时降级 - 直接查询数据库
                log.warn("Failed to acquire lock within timeout for post: {}, falling back to database", postId);
                return delegate.getPostContent(postId);
            }

            try {
                // Step 3: DCL 双重检查 - 获取锁后再次检查缓存
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CacheConstants.NULL_VALUE.equals(cached)) {
                        log.debug("DCL: Cache hit (null value) after acquiring lock for post: {}", postId);
                        return null;
                    }
                    log.debug("DCL: Cache hit after acquiring lock for post: {}", postId);
                    return (PostContent) cached;
                }

                // Step 4: 查询数据库
                log.debug("Loading content from database for post: {}", postId);
                PostContent content = delegate.getPostContent(postId);

                // Step 5: 写入缓存
                cacheContent(cacheKey, content);

                return content;

            } finally {
                // 确保锁被释放
                if (lock.isHeldByCurrentThread()) {
                    try {
                        lock.unlock();
                        log.debug("Released lock for post: {}", postId);
                    } catch (Exception e) {
                        log.error("Failed to release lock for post: {}, will rely on auto-expiration", postId, e);
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for post: {}, falling back to database", postId);
            return delegate.getPostContent(postId);
        } catch (org.redisson.client.RedisException e) {
            // Redisson 连接异常，降级查询数据库
            log.error("Redisson connection failed for postId={}, falling back to database: {}", 
                    postId, e.getMessage());
            return delegate.getPostContent(postId);
        } catch (Exception e) {
            log.error("Unexpected error during lock operation for post: {}", postId, e);
            return delegate.getPostContent(postId);
        }
    }

    @Override
    public void updatePost(Post post, PostContent content) {
        Long postId = post.getId();
        
        // 更新数据库
        delegate.updatePost(post, content);
        
        // 删除缓存（Cache-Aside 模式）
        try {
            evictCache(postId);
        } catch (Exception e) {
            log.warn("Failed to evict cache after update: {}", e.getMessage());
        }
    }

    @Override
    public void deletePost(Long postId) {
        // 删除数据库
        delegate.deletePost(postId);
        
        // 删除缓存
        try {
            evictCache(postId);
        } catch (Exception e) {
            log.warn("Failed to evict cache after delete: {}", e.getMessage());
        }
    }

    // ==================== 缓存辅助方法 ====================

    /**
     * 缓存文章内容
     */
    private void cacheContent(String key, PostContent content) {
        try {
            if (content != null) {
                // 添加随机抖动防止缓存雪崩
                long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
                redisTemplate.opsForValue().set(key, content, ttlWithJitter, TimeUnit.SECONDS);
                log.debug("Cached content: key={}, ttl={}s", key, ttlWithJitter);
            } else {
                // 缓存空值防止缓存穿透
                redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                        CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("Cached null value for content: key={}", key);
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            log.error("Redis connection failed during cache write: key={}, error={}", key, e.getMessage());
            // 缓存写入失败不影响业务
        } catch (Exception e) {
            log.warn("Failed to cache content: key={}, error={}", key, e.getMessage());
            // 缓存写入失败不影响业务
        }
    }

    /**
     * 缓存文章完整详情
     */
    private void cacheFullDetail(String key, PostDetail detail) {
        try {
            if (detail != null) {
                // 添加随机抖动防止缓存雪崩
                long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
                redisTemplate.opsForValue().set(key, detail, ttlWithJitter, TimeUnit.SECONDS);
                log.debug("Cached full detail: key={}, ttl={}s", key, ttlWithJitter);
            } else {
                // 缓存空值防止缓存穿透
                redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                        CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("Cached null value for full detail: key={}", key);
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            log.error("Redis connection failed during cache write: key={}, error={}", key, e.getMessage());
            // 缓存写入失败不影响业务
        } catch (Exception e) {
            log.warn("Failed to cache full detail: key={}, error={}", key, e.getMessage());
            // 缓存写入失败不影响业务
        }
    }

    /**
     * 删除缓存
     */
    private void evictCache(Long postId) {
        String contentKey = PostRedisKeys.content(postId);
        String fullDetailKey = PostRedisKeys.fullDetail(postId);
        String postDetailKey = PostRedisKeys.detail(postId);
        
        redisTemplate.delete(contentKey);
        redisTemplate.delete(fullDetailKey);
        redisTemplate.delete(postDetailKey);
        
        log.debug("Evicted cache for post: {}", postId);
    }

    /**
     * 生成随机抖动值
     */
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
    }

    /**
     * 预热文章内容缓存
     *
     * @param postId 文章ID
     */
    public void warmUpContentCache(Long postId) {
        try {
            PostContent content = delegate.getPostContent(postId);
            if (content != null) {
                cacheContent(PostRedisKeys.content(postId), content);
                log.debug("Warmed up content cache for post: {}", postId);
            }
        } catch (Exception e) {
            log.warn("Failed to warm up content cache for post {}: {}", postId, e.getMessage());
        }
    }

    /**
     * 预热文章完整详情缓存
     *
     * @param postId 文章ID
     */
    public void warmUpFullDetailCache(Long postId) {
        try {
            PostDetail detail = delegate.getPostFullDetail(postId);
            if (detail != null) {
                cacheFullDetail(PostRedisKeys.fullDetail(postId), detail);
                log.debug("Warmed up full detail cache for post: {}", postId);
            }
        } catch (Exception e) {
            log.warn("Failed to warm up full detail cache for post {}: {}", postId, e.getMessage());
        }
    }

    /**
     * 批量查询文章内容（带缓存优化）
     * 
     * 优化策略：
     * 1. 批量查询缓存
     * 2. 区分热点数据和非热点数据
     * 3. 热点数据使用分布式锁（按ID排序避免死锁）
     * 4. 非热点数据直接批量查询数据库
     * 
     * @param postIds 文章ID集合
     * @return 文章内容映射（postId -> PostContent）
     */
    public java.util.Map<Long, PostContent> getPostContentBatch(java.util.Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new java.util.HashMap<>();
        }

        java.util.Map<Long, PostContent> result = new java.util.HashMap<>();
        java.util.Set<Long> cacheMissIds = new java.util.HashSet<>();
        java.util.Set<Long> hotDataIds = new java.util.HashSet<>();
        java.util.Set<Long> nonHotDataIds = new java.util.HashSet<>();

        // Step 1: 批量查询缓存
        for (Long postId : postIds) {
            String cacheKey = PostRedisKeys.content(postId);
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (!CacheConstants.NULL_VALUE.equals(cached) && cached instanceof PostContent) {
                        result.put(postId, (PostContent) cached);
                    }
                    // 空值缓存或已缓存，跳过
                } else {
                    cacheMissIds.add(postId);
                }
            } catch (Exception e) {
                log.warn("Cache lookup failed for postId={}: {}", postId, e.getMessage());
                cacheMissIds.add(postId);
            }
        }

        if (cacheMissIds.isEmpty()) {
            log.debug("Batch query: all {} posts found in cache", postIds.size());
            return result;
        }

        log.debug("Batch query: {} cache hits, {} cache misses", result.size(), cacheMissIds.size());

        // Step 2: 区分热点数据和非热点数据
        for (Long postId : cacheMissIds) {
            hotDataIdentifier.recordAccess(ENTITY_TYPE_POST, postId);
            boolean isHot = hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId) 
                    || hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId);
            
            if (isHot) {
                hotDataIds.add(postId);
            } else {
                nonHotDataIds.add(postId);
            }
        }

        log.debug("Batch query: {} hot data, {} non-hot data", hotDataIds.size(), nonHotDataIds.size());

        // Step 3: 非热点数据直接批量查询数据库
        if (!nonHotDataIds.isEmpty()) {
            for (Long postId : nonHotDataIds) {
                try {
                    PostContent content = delegate.getPostContent(postId);
                    if (content != null) {
                        result.put(postId, content);
                        cacheContent(PostRedisKeys.content(postId), content);
                    } else {
                        cacheContent(PostRedisKeys.content(postId), null);
                    }
                } catch (Exception e) {
                    log.error("Failed to query non-hot post {}: {}", postId, e.getMessage(), e);
                }
            }
        }

        // Step 4: 热点数据使用分布式锁（按ID排序避免死锁）
        if (!hotDataIds.isEmpty()) {
            java.util.List<Long> sortedHotIds = new java.util.ArrayList<>(hotDataIds);
            java.util.Collections.sort(sortedHotIds);
            
            for (Long postId : sortedHotIds) {
                try {
                    PostContent content = getPostContent(postId);
                    if (content != null) {
                        result.put(postId, content);
                    }
                } catch (Exception e) {
                    log.error("Failed to query hot post {}: {}", postId, e.getMessage(), e);
                }
            }
        }

        log.debug("Batch query completed: {} posts found", result.size());
        return result;
    }
}
