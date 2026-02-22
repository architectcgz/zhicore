package com.zhicore.content.infrastructure.repository;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 带缓存的文章仓储
 * 
 * 使用装饰器模式包装 PostRepositoryImpl，添加缓存功能
 * 实现 Cache-Aside 模式：
 * - 读：先查缓存，未命中再查数据库，然后写缓存
 * - 写：先更新数据库，再删除缓存
 * 
 * 提供缓存穿透、雪崩防护
 *
 * @author ZhiCore Team
 */
@Slf4j
@Primary
@Repository
public class CachedPostRepository implements PostRepository {

    private final PostRepository delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public CachedPostRepository(
            RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties,
            @Qualifier("postRepositoryImpl") PostRepository delegate) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public void save(Post post) {
        delegate.save(post);
        // 保存后缓存文章
        try {
            cachePost(PostRedisKeys.detail(post.getId()), post);
        } catch (Exception e) {
            log.warn("Failed to cache post after save: {}", e.getMessage());
        }
    }

    @Override
    public void update(Post post) {
        delegate.update(post);
        // 更新后删除缓存（Cache-Aside 模式）
        try {
            evictCache(post.getId());
        } catch (Exception e) {
            log.warn("Failed to evict cache after update: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Post> findById(Long id) {
        String key = PostRedisKeys.detail(id);

        try {
            // 1. 查缓存
            Object cached = redisTemplate.opsForValue().get(key);

            // 2. 命中缓存
            if (cached != null) {
                if (CacheConstants.NULL_VALUE.equals(cached)) {
                    log.debug("Cache hit (null value): key={}", key);
                    return Optional.empty();
                }
                log.debug("Cache hit: key={}", key);
                return Optional.of((Post) cached);
            }

            // 3. 未命中，查数据库
            log.debug("Cache miss: key={}", key);
            Optional<Post> postOpt = delegate.findById(id);

            // 4. 写缓存
            cachePost(key, postOpt.orElse(null));

            return postOpt;
        } catch (Exception e) {
            log.warn("Cache lookup failed, falling back to database: {}", e.getMessage());
            return delegate.findById(id);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, Post> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 使用 Pipeline 批量查询缓存
            List<String> keys = ids.stream()
                    .map(PostRedisKeys::detail)
                    .collect(Collectors.toList());

            List<Object> cachedResults = redisTemplate.opsForValue().multiGet(keys);

            Map<Long, Post> result = new HashMap<>();
            List<Long> missedIds = new ArrayList<>();

            // 处理缓存结果
            for (int i = 0; i < ids.size(); i++) {
                Long postId = ids.get(i);
                Object cached = cachedResults != null ? cachedResults.get(i) : null;

                if (cached != null) {
                    if (!CacheConstants.NULL_VALUE.equals(cached)) {
                        result.put(postId, (Post) cached);
                    }
                    // NULL_VALUE 表示数据库中不存在，不需要再查
                } else {
                    missedIds.add(postId);
                }
            }

            // 查询未命中的数据
            if (!missedIds.isEmpty()) {
                log.debug("Cache miss for {} posts, querying database", missedIds.size());
                Map<Long, Post> dbPosts = delegate.findByIds(missedIds);
                result.putAll(dbPosts);

                // 回填缓存
                for (Long missedId : missedIds) {
                    Post post = dbPosts.get(missedId);
                    cachePost(PostRedisKeys.detail(missedId), post);
                }
            }

            return result;
        } catch (Exception e) {
            log.warn("Batch cache lookup failed, falling back to database: {}", e.getMessage());
            return delegate.findByIds(ids);
        }
    }

    @Override
    public List<Post> findByOwnerId(Long ownerId, PostStatus status, int offset, int limit) {
        // 列表查询不走缓存，直接查数据库
        return delegate.findByOwnerId(ownerId, status, offset, limit);
    }

    @Override
    public List<Post> findByOwnerIdCursor(Long ownerId, PostStatus status, LocalDateTime cursor, int limit) {
        // 游标分页不走缓存，直接查数据库
        return delegate.findByOwnerIdCursor(ownerId, status, cursor, limit);
    }

    @Override
    public List<Post> findPublished(int offset, int limit) {
        // 列表查询不走缓存，直接查数据库
        return delegate.findPublished(offset, limit);
    }

    @Override
    public List<Post> findPublishedCursor(LocalDateTime cursor, int limit) {
        // 游标分页不走缓存，直接查数据库
        return delegate.findPublishedCursor(cursor, limit);
    }

    @Override
    public List<Post> findScheduledPostsDue(LocalDateTime now) {
        // 定时任务查询不走缓存
        return delegate.findScheduledPostsDue(now);
    }

    @Override
    public int countByOwnerId(Long ownerId, PostStatus status) {
        return delegate.countByOwnerId(ownerId, status);
    }

    @Override
    public long countPublished() {
        return delegate.countPublished();
    }

    @Override
    public void delete(Long id) {
        delegate.delete(id);
        // 删除后清除缓存
        try {
            evictCache(id);
        } catch (Exception e) {
            log.warn("Failed to evict cache after delete: {}", e.getMessage());
        }
    }

    @Override
    public boolean existsById(Long id) {
        // 先查缓存
        String key = PostRedisKeys.detail(id);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return !CacheConstants.NULL_VALUE.equals(cached);
            }
        } catch (Exception e) {
            log.warn("Cache check failed: {}", e.getMessage());
        }
        return delegate.existsById(id);
    }

    // ==================== 缓存辅助方法 ====================

    /**
     * 缓存文章
     */
    private void cachePost(String key, Post post) {
        if (post != null) {
            // 添加随机抖动防止缓存雪崩
            long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
            redisTemplate.opsForValue().set(key, post, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached post: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            // 缓存空值防止缓存穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached null value: key={}", key);
        }
    }

    /**
     * 删除缓存
     */
    public void evictCache(Long postId) {
        String key = PostRedisKeys.detail(postId);
        redisTemplate.delete(key);
        log.debug("Evicted cache: key={}", key);
    }

    /**
     * 生成随机抖动值
     */
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
    }

    /**
     * 预热文章缓存
     *
     * @param postId 文章ID
     */
    public void warmUpCache(Long postId) {
        try {
            Post post = delegate.findById(postId).orElse(null);
            if (post != null) {
                cachePost(PostRedisKeys.detail(postId), post);
                log.debug("Warmed up cache for post: {}", postId);
            }
        } catch (Exception e) {
            log.warn("Failed to warm up cache for post {}: {}", postId, e.getMessage());
        }
    }

    /**
     * 批量预热文章缓存
     *
     * @param postIds 文章ID列表
     */
    public void warmUpCacheBatch(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        
        try {
            Map<Long, Post> posts = delegate.findByIds(postIds);
            for (Map.Entry<Long, Post> entry : posts.entrySet()) {
                cachePost(PostRedisKeys.detail(entry.getKey()), entry.getValue());
            }
            log.debug("Warmed up cache for {} posts", posts.size());
        } catch (Exception e) {
            log.warn("Failed to warm up cache batch: {}", e.getMessage());
        }
    }

    /**
     * 批量检查点赞状态（使用 Redis Pipeline 优化）
     * 
     * @param userId 用户ID
     * @param postIds 文章ID列表
     * @return 点赞状态映射 (postId -> isLiked)
     */
    public Map<Long, Boolean> batchCheckLikedStatus(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 使用 Pipeline 批量查询 Redis
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Long postId : postIds) {
                    String key = PostRedisKeys.userLiked(userId, postId);
                    connection.keyCommands().exists(key.getBytes());
                }
                return null;
            });

            Map<Long, Boolean> likedMap = new HashMap<>();
            for (int i = 0; i < postIds.size(); i++) {
                Long postId = postIds.get(i);
                Object result = results.get(i);
                // exists 返回 Long 类型，1 表示存在，0 表示不存在
                boolean liked = result instanceof Long && (Long) result > 0;
                likedMap.put(postId, liked);
            }

            return likedMap;
        } catch (Exception e) {
            log.warn("Batch check liked status failed: {}", e.getMessage());
            // 返回空 map，调用方可以选择降级处理
            return Collections.emptyMap();
        }
    }

    /**
     * 批量检查收藏状态（使用 Redis Pipeline 优化）
     * 
     * @param userId 用户ID
     * @param postIds 文章ID列表
     * @return 收藏状态映射 (postId -> isFavorited)
     */
    public Map<Long, Boolean> batchCheckFavoritedStatus(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 使用 Pipeline 批量查询 Redis
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Long postId : postIds) {
                    String key = PostRedisKeys.userFavorited(userId, postId);
                    connection.keyCommands().exists(key.getBytes());
                }
                return null;
            });

            Map<Long, Boolean> favoritedMap = new HashMap<>();
            for (int i = 0; i < postIds.size(); i++) {
                Long postId = postIds.get(i);
                Object result = results.get(i);
                // exists 返回 Long 类型，1 表示存在，0 表示不存在
                boolean favorited = result instanceof Long && (Long) result > 0;
                favoritedMap.put(postId, favorited);
            }

            return favoritedMap;
        } catch (Exception e) {
            log.warn("Batch check favorited status failed: {}", e.getMessage());
            // 返回空 map，调用方可以选择降级处理
            return Collections.emptyMap();
        }
    }

    /**
     * 批量获取文章统计数据（使用 Redis Pipeline 优化）
     * 
     * @param postIds 文章ID列表
     * @return 统计数据映射 (postId -> stats map)
     */
    public Map<Long, Map<String, Long>> batchGetStats(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 使用 Pipeline 批量查询统计数据
            List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Long postId : postIds) {
                    // 获取点赞数
                    connection.stringCommands().get(PostRedisKeys.likeCount(postId).getBytes());
                    // 获取收藏数
                    connection.stringCommands().get(PostRedisKeys.favoriteCount(postId).getBytes());
                    // 获取评论数
                    connection.stringCommands().get(PostRedisKeys.commentCount(postId).getBytes());
                    // 获取浏览数
                    connection.stringCommands().get(PostRedisKeys.viewCount(postId).getBytes());
                }
                return null;
            });

            Map<Long, Map<String, Long>> statsMap = new HashMap<>();
            for (int i = 0; i < postIds.size(); i++) {
                Long postId = postIds.get(i);
                int baseIndex = i * 4;

                Map<String, Long> stats = new HashMap<>();
                stats.put("likeCount", parseLong(results.get(baseIndex)));
                stats.put("favoriteCount", parseLong(results.get(baseIndex + 1)));
                stats.put("commentCount", parseLong(results.get(baseIndex + 2)));
                stats.put("viewCount", parseLong(results.get(baseIndex + 3)));

                statsMap.put(postId, stats);
            }

            return statsMap;
        } catch (Exception e) {
            log.warn("Batch get stats failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 解析 Long 值
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        if (value instanceof byte[]) {
            try {
                return Long.parseLong(new String((byte[]) value));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    public List<Post> findByConditions(String keyword, String status, Long authorId, int offset, int limit) {
        // 管理查询不使用缓存，直接查询数据库
        return delegate.findByConditions(keyword, status, authorId, offset, limit);
    }

    @Override
    public long countByConditions(String keyword, String status, Long authorId) {
        // 管理查询不使用缓存，直接查询数据库
        return delegate.countByConditions(keyword, status, authorId);
    }

    @Override
    public int updateAuthorInfo(Long userId, String nickname, String avatarId, Long version) {
        return 0;
    }

    @Override
    public List<Post> findByOwnerNameAndVersion(String ownerName, Long version, int limit) {
        return List.of();
    }
}
