package com.zhicore.content.infrastructure.service;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.service.DraftService;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 带缓存的草稿管理器
 * 
 * 使用装饰器模式包装 DraftManagerImpl，添加缓存功能
 * 实现 Cache-Aside 模式：
 * - 读：先查缓存，未命中再查数据库，然后写缓存
 * - 写：先更新数据库，再更新缓存（草稿需要立即可见）
 * 
 * 缓存策略：
 * 1. 单个草稿缓存 - TTL: 5分钟（草稿频繁更新，较短TTL）
 * 2. 用户草稿列表缓存 - TTL: 3分钟
 * 3. 空值缓存防止缓存穿透 - TTL: 1分钟
 *
 * @author ZhiCore Team
 */
@Slf4j
@Primary
@Service
public class CachedDraftService implements DraftService {

    private final DraftService delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public CachedDraftService(
            RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties,
            @Qualifier("draftServiceImpl") DraftService delegate) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public void saveDraft(Long postId, Long userId, String content, boolean isAutoSave) {
        // 保存到数据库
        delegate.saveDraft(postId, userId, content, isAutoSave);
        
        // 删除缓存，让下次查询时重新加载最新数据
        // 注意：草稿保存非常频繁，使用删除策略而不是更新策略，避免缓存和数据库不一致
        try {
            evictDraftCache(postId, userId);
        } catch (Exception e) {
            log.warn("Failed to evict draft cache after save: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<DraftSnapshot> getLatestDraft(Long postId, Long userId) {
        String key = PostRedisKeys.draft(postId, userId);

        try {
            // 1. 查缓存
            Object cached = redisTemplate.opsForValue().get(key);

            // 2. 命中缓存
            if (cached != null) {
                if (CacheConstants.isNullMarker(cached)) {
                    log.debug("Cache hit (null value) for draft: key={}", key);
                    return Optional.empty();
                }
                log.debug("Cache hit for draft: key={}", key);
                return Optional.of((DraftSnapshot) cached);
            }

            // 3. 未命中，查数据库
            log.debug("Cache miss for draft: key={}", key);
            Optional<DraftSnapshot> draft = delegate.getLatestDraft(postId, userId);

            // 4. 写缓存
            cacheDraft(key, draft.orElse(null));

            return draft;
        } catch (Exception e) {
            log.warn("Cache lookup failed for draft, falling back to database: {}", e.getMessage());
            return delegate.getLatestDraft(postId, userId);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DraftSnapshot> getUserDrafts(Long userId) {
        String key = PostRedisKeys.userDrafts(userId);

        try {
            // 1. 查缓存
            Object cached = redisTemplate.opsForValue().get(key);

            // 2. 命中缓存
            if (cached != null) {
                if (CacheConstants.isNullMarker(cached)) {
                    log.debug("Cache hit (empty list) for user drafts: key={}", key);
                    return List.of();
                }
                log.debug("Cache hit for user drafts: key={}", key);
                return (List<DraftSnapshot>) cached;
            }

            // 3. 未命中，查数据库
            log.debug("Cache miss for user drafts: key={}", key);
            List<DraftSnapshot> drafts = delegate.getUserDrafts(userId);

            // 4. 写缓存
            cacheDraftList(key, drafts);

            return drafts;
        } catch (Exception e) {
            log.warn("Cache lookup failed for user drafts, falling back to database: {}", e.getMessage());
            return delegate.getUserDrafts(userId);
        }
    }

    @Override
    public void deleteDraft(Long postId, Long userId) {
        // 删除数据库
        delegate.deleteDraft(postId, userId);
        
        // 删除缓存
        try {
            evictDraftCache(postId, userId);
        } catch (Exception e) {
            log.warn("Failed to evict draft cache after delete: {}", e.getMessage());
        }
    }

    @Override
    public long cleanExpiredDrafts(int expireDays) {
        // 清理过期草稿
        long deletedCount = delegate.cleanExpiredDrafts(expireDays);
        
        // 注意：这里不清理缓存，因为过期草稿的缓存会自动过期
        // 如果需要立即清理，可以使用 Redis SCAN 命令查找并删除相关缓存
        
        return deletedCount;
    }

    // ==================== 缓存辅助方法 ====================

    /**
     * 缓存草稿
     */
    private void cacheDraft(String key, DraftSnapshot draft) {
        if (draft != null) {
            // 添加随机抖动防止缓存雪崩
            long ttlWithJitter = cacheProperties.getDraft().getTtl() + randomJitter();
            redisTemplate.opsForValue().set(key, draft, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached draft: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            // 缓存空值防止缓存穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_MARKER,
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached null value for draft: key={}", key);
        }
    }

    /**
     * 缓存草稿列表
     */
    private void cacheDraftList(String key, List<DraftSnapshot> drafts) {
        if (drafts != null && !drafts.isEmpty()) {
            // 添加随机抖动防止缓存雪崩
            long ttlWithJitter = cacheProperties.getDraft().getListTtl() + randomJitter();
            redisTemplate.opsForValue().set(key, drafts, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached draft list: key={}, size={}, ttl={}s", key, drafts.size(), ttlWithJitter);
        } else {
            // 缓存空列表防止缓存穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_MARKER,
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached empty draft list: key={}", key);
        }
    }

    /**
     * 删除草稿缓存
     */
    private void evictDraftCache(Long postId, Long userId) {
        String draftKey = PostRedisKeys.draft(postId, userId);
        String userDraftsKey = PostRedisKeys.userDrafts(userId);
        
        redisTemplate.delete(draftKey);
        redisTemplate.delete(userDraftsKey);
        
        log.debug("Evicted draft cache for postId={}, userId={}", postId, userId);
    }

    /**
     * 生成随机抖动值
     */
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, cacheProperties.getJitter().getMaxSeconds());
    }

    /**
     * 预热草稿缓存
     *
     * @param postId 文章ID
     * @param userId 用户ID
     */
    public void warmUpDraftCache(Long postId, Long userId) {
        try {
            Optional<DraftSnapshot> draft = delegate.getLatestDraft(postId, userId);
            draft.ifPresent(d -> cacheDraft(PostRedisKeys.draft(postId, userId), d));
            log.debug("Warmed up draft cache for postId={}, userId={}", postId, userId);
        } catch (Exception e) {
            log.warn("Failed to warm up draft cache for postId={}, userId={}: {}", 
                    postId, userId, e.getMessage());
        }
    }

    /**
     * 预热用户草稿列表缓存
     *
     * @param userId 用户ID
     */
    public void warmUpUserDraftsCache(Long userId) {
        try {
            List<DraftSnapshot> drafts = delegate.getUserDrafts(userId);
            cacheDraftList(PostRedisKeys.userDrafts(userId), drafts);
            log.debug("Warmed up user drafts cache for userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to warm up user drafts cache for userId={}: {}", userId, e.getMessage());
        }
    }
}
