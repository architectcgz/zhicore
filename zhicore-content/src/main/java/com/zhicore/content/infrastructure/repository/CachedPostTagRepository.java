package com.zhicore.content.infrastructure.repository;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.infrastructure.cache.TagRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Cached PostTag Repository
 * 
 * 为 PostTagRepository 添加 Redis 缓存层
 * 
 * 缓存策略：
 * - post:tags:{postId} - Post 的 Tag ID 列表缓存，TTL: 30 minutes
 * - tag:posts:{tagId}:page:{page}:{size} - Tag 下的 Post ID 列表缓存（分页），TTL: 30 minutes
 * - 写操作（attach/detach）时失效相关缓存
 * - 添加随机抖动防止缓存雪崩
 * 
 * Requirements: 5.1
 *
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@Primary
public class CachedPostTagRepository implements PostTagRepository {

    private static final Random RANDOM = new Random();

    private final PostTagRepository delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public CachedPostTagRepository(
            RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties,
            @Qualifier("postTagRepositoryImpl") PostTagRepository delegate) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public void attach(Long postId, Long tagId) {
        delegate.attach(postId, tagId);
        
        // 失效缓存
        evictPostTagsCache(postId);
        evictTagPostsCache(tagId);
    }

    @Override
    public void attachBatch(Long postId, List<Long> tagIds) {
        delegate.attachBatch(postId, tagIds);
        
        // 失效缓存
        evictPostTagsCache(postId);
        for (Long tagId : tagIds) {
            evictTagPostsCache(tagId);
        }
    }

    @Override
    public void detach(Long postId, Long tagId) {
        delegate.detach(postId, tagId);
        
        // 失效缓存
        evictPostTagsCache(postId);
        evictTagPostsCache(tagId);
    }

    @Override
    public void detachAllByPostId(Long postId) {
        // 先查询所有关联的 Tag ID，用于失效缓存
        List<Long> tagIds = delegate.findTagIdsByPostId(postId);
        
        delegate.detachAllByPostId(postId);
        
        // 失效缓存
        evictPostTagsCache(postId);
        for (Long tagId : tagIds) {
            evictTagPostsCache(tagId);
        }
    }

    @Override
    public List<Long> findTagIdsByPostId(Long postId) {
        String key = TagRedisKeys.postTags(postId);
        
        try {
            // 1. 查缓存
            Object cached = redisTemplate.opsForValue().get(key);
            
            // 2. 命中缓存
            if (cached != null) {
                if (CacheConstants.NULL_VALUE.equals(cached)) {
                    log.debug("Cache hit (empty list): key={}", key);
                    return List.of();
                }
                log.debug("Cache hit: key={}", key);
                return (List<Long>) cached;
            }
            
            // 3. 缓存未命中，查询数据库
            log.debug("Cache miss: key={}", key);
            List<Long> tagIds = delegate.findTagIdsByPostId(postId);
            
            // 4. 写入缓存
            cacheTagIds(key, tagIds);
            
            return tagIds;
        } catch (Exception e) {
            log.error("Cache operation failed for key={}, falling back to database", key, e);
            return delegate.findTagIdsByPostId(postId);
        }
    }

    @Override
    public List<Long> findPostIdsByTagId(Long tagId) {
        // 不缓存完整列表，只缓存分页结果
        return delegate.findPostIdsByTagId(tagId);
    }

    @Override
    public Page<Long> findPostIdsByTagId(Long tagId, Pageable pageable) {
        // 分页查询缓存
        String key = TagRedisKeys.tagPosts(tagId, pageable.getPageNumber(), pageable.getPageSize());
        
        try {
            // 1. 查缓存
            Object cached = redisTemplate.opsForValue().get(key);
            
            // 2. 命中缓存
            if (cached != null) {
                log.debug("Cache hit: key={}", key);
                return (Page<Long>) cached;
            }
            
            // 3. 缓存未命中，查询数据库
            log.debug("Cache miss: key={}", key);
            Page<Long> postIdPage = delegate.findPostIdsByTagId(tagId, pageable);
            
            // 4. 写入缓存
            cachePostIdPage(key, postIdPage);
            
            return postIdPage;
        } catch (Exception e) {
            log.error("Cache operation failed for key={}, falling back to database", key, e);
            return delegate.findPostIdsByTagId(tagId, pageable);
        }
    }

    @Override
    public boolean exists(Long postId, Long tagId) {
        // 存在性检查不缓存，直接查数据库
        return delegate.exists(postId, tagId);
    }

    @Override
    public int countPostsByTagId(Long tagId) {
        // 统计不缓存，直接查数据库
        return delegate.countPostsByTagId(tagId);
    }

    @Override
    public int countTagsByPostId(Long postId) {
        // 统计不缓存，直接查数据库
        return delegate.countTagsByPostId(postId);
    }

    @Override
    public java.util.Map<Long, List<Long>> findTagIdsByPostIds(List<Long> postIds) {
        // 批量查询不缓存，直接查数据库
        // 批量查询通常用于列表场景，缓存收益不高
        return delegate.findTagIdsByPostIds(postIds);
    }

    /**
     * 缓存 Tag ID 列表
     *
     * @param key Redis key
     * @param tagIds Tag ID 列表
     */
    private void cacheTagIds(String key, List<Long> tagIds) {
        try {
            if (tagIds != null && !tagIds.isEmpty()) {
                // 缓存实际值，添加随机抖动防止缓存雪崩
                long ttlWithJitter = CacheConstants.POST_TAG_CACHE_TTL_SECONDS + randomJitter();
                redisTemplate.opsForValue().set(key, tagIds, ttlWithJitter, TimeUnit.SECONDS);
                log.debug("Cached tag IDs: key={}, size={}, ttl={}s", key, tagIds.size(), ttlWithJitter);
            } else {
                // 缓存空值防止缓存穿透
                redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                        CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("Cached empty tag IDs: key={}", key);
            }
        } catch (Exception e) {
            log.error("Failed to cache tag IDs: key={}", key, e);
        }
    }

    /**
     * 缓存 Post ID 分页结果
     *
     * @param key Redis key
     * @param postIdPage Post ID 分页对象
     */
    private void cachePostIdPage(String key, Page<Long> postIdPage) {
        try {
            // 缓存分页结果，添加随机抖动防止缓存雪崩
            long ttlWithJitter = CacheConstants.POST_TAG_CACHE_TTL_SECONDS + randomJitter();
            redisTemplate.opsForValue().set(key, postIdPage, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached post ID page: key={}, size={}, ttl={}s", 
                    key, postIdPage.getContent().size(), ttlWithJitter);
        } catch (Exception e) {
            log.error("Failed to cache post ID page: key={}", key, e);
        }
    }

    /**
     * 失效 Post 的 Tag 列表缓存
     *
     * @param postId Post ID
     */
    private void evictPostTagsCache(Long postId) {
        try {
            String key = TagRedisKeys.postTags(postId);
            redisTemplate.delete(key);
            log.debug("Evicted post tags cache: key={}", key);
        } catch (Exception e) {
            log.error("Failed to evict post tags cache for postId={}", postId, e);
        }
    }

    /**
     * 失效 Tag 的 Post 列表缓存（所有分页）
     *
     * @param tagId Tag ID
     */
    private void evictTagPostsCache(Long tagId) {
        try {
            // 使用通配符删除所有分页缓存
            String pattern = "tag:posts:" + tagId + ":page:*";
            redisTemplate.keys(pattern).forEach(key -> {
                redisTemplate.delete(key);
                log.debug("Evicted tag posts cache: key={}", key);
            });
        } catch (Exception e) {
            log.error("Failed to evict tag posts cache for tagId={}", tagId, e);
        }
    }

    /**
     * 生成随机抖动（0-60秒）
     *
     * @return 随机秒数
     */
    private long randomJitter() {
        return RANDOM.nextInt(CacheConstants.MAX_JITTER_SECONDS);
    }
}
