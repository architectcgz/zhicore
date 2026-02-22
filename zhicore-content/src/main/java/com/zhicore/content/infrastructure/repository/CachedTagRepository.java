package com.zhicore.content.infrastructure.repository;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.cache.TagRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Cached Tag Repository
 * 
 * 为 TagRepository 添加 Redis 缓存层
 * 
 * 缓存策略：
 * - tag:slug:{slug} - Tag 详情缓存（通过 slug 查询），TTL: 1 hour
 * - tag:id:{tagId} - Tag 详情缓存（通过 ID 查询），TTL: 1 hour
 * - 缓存空值防止缓存穿透
 * - 添加随机抖动防止缓存雪崩
 * 
 * Requirements: 5.1
 *
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@Primary
public class CachedTagRepository implements TagRepository {

    private static final Random RANDOM = new Random();

    private final TagRepository delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public CachedTagRepository(
            RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties,
            @Qualifier("tagRepositoryImpl") TagRepository delegate) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public Tag save(Tag tag) {
        Tag saved = delegate.save(tag);
        
        // 保存后失效缓存
        evictCache(saved.getSlug());
        if (saved.getId() != null) {
            evictCacheById(saved.getId());
        }
        
        return saved;
    }

    @Override
    public Optional<Tag> findById(Long id) {
        String key = TagRedisKeys.byId(id);
        
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
                return Optional.of((Tag) cached);
            }
            
            // 3. 缓存未命中，查询数据库
            log.debug("Cache miss: key={}", key);
            Optional<Tag> tag = delegate.findById(id);
            
            // 4. 写入缓存
            cacheTag(key, tag.orElse(null));
            
            return tag;
        } catch (Exception e) {
            log.error("Cache operation failed for key={}, falling back to database", key, e);
            return delegate.findById(id);
        }
    }

    @Override
    public Optional<Tag> findBySlug(String slug) {
        String key = TagRedisKeys.bySlug(slug);
        
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
                return Optional.of((Tag) cached);
            }
            
            // 3. 缓存未命中，查询数据库
            log.debug("Cache miss: key={}", key);
            Optional<Tag> tag = delegate.findBySlug(slug);
            
            // 4. 写入缓存
            cacheTag(key, tag.orElse(null));
            
            return tag;
        } catch (Exception e) {
            log.error("Cache operation failed for key={}, falling back to database", key, e);
            return delegate.findBySlug(slug);
        }
    }

    @Override
    public List<Tag> findBySlugIn(List<String> slugs) {
        // 批量查询不缓存，直接查数据库
        return delegate.findBySlugIn(slugs);
    }

    @Override
    public List<Tag> findByIdIn(List<Long> ids) {
        // 批量查询不缓存，直接查数据库
        return delegate.findByIdIn(ids);
    }

    @Override
    public boolean existsBySlug(String slug) {
        // 存在性检查不缓存，直接查数据库
        return delegate.existsBySlug(slug);
    }

    @Override
    public Page<Tag> findAll(Pageable pageable) {
        // 分页查询不缓存，直接查数据库
        return delegate.findAll(pageable);
    }

    @Override
    public List<Tag> searchByName(String keyword, int limit) {
        // 搜索不缓存，直接查数据库
        return delegate.searchByName(keyword, limit);
    }

    @Override
    public List<Map<String, Object>> findHotTags(int limit) {
        // 热门标签缓存在 TagApplicationService 层处理
        return delegate.findHotTags(limit);
    }

    @Override
    public void deleteById(Long id) {
        // 先查询 Tag 以获取 slug（用于失效缓存）
        Optional<Tag> tag = delegate.findById(id);
        
        // 删除 Tag
        delegate.deleteById(id);
        
        // 失效缓存
        if (tag.isPresent()) {
            evictCache(tag.get().getSlug());
        }
        evictCacheById(id);
    }

    @Override
    public java.util.Map<Long, List<Tag>> findTagsByPostIds(List<Long> postIds) {
        // 批量查询不缓存，直接查数据库
        // 批量查询通常用于列表场景，缓存收益不高
        return delegate.findTagsByPostIds(postIds);
    }

    /**
     * 缓存 Tag
     *
     * @param key Redis key
     * @param tag Tag 对象（可能为 null）
     */
    private void cacheTag(String key, Tag tag) {
        try {
            if (tag != null) {
                // 缓存实际值，添加随机抖动防止缓存雪崩
                long ttlWithJitter = CacheConstants.TAG_CACHE_TTL_SECONDS + randomJitter();
                redisTemplate.opsForValue().set(key, tag, ttlWithJitter, TimeUnit.SECONDS);
                log.debug("Cached tag: key={}, ttl={}s", key, ttlWithJitter);
            } else {
                // 缓存空值防止缓存穿透
                redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE,
                        CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("Cached null value: key={}", key);
            }
        } catch (Exception e) {
            log.error("Failed to cache tag: key={}", key, e);
        }
    }

    /**
     * 失效缓存（通过 slug）
     *
     * @param slug Tag slug
     */
    public void evictCache(String slug) {
        try {
            String key = TagRedisKeys.bySlug(slug);
            redisTemplate.delete(key);
            log.debug("Evicted cache: key={}", key);
        } catch (Exception e) {
            log.error("Failed to evict cache for slug={}", slug, e);
        }
    }

    /**
     * 失效缓存（通过 ID）
     *
     * @param tagId Tag ID
     */
    public void evictCacheById(Long tagId) {
        try {
            String key = TagRedisKeys.byId(tagId);
            redisTemplate.delete(key);
            log.debug("Evicted cache: key={}", key);
        } catch (Exception e) {
            log.error("Failed to evict cache for tagId={}", tagId, e);
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
