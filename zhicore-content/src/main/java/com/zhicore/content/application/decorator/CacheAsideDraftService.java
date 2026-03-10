package com.zhicore.content.application.decorator;

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
 * 草稿读取缓存装饰器
 *
 * 装饰 DraftService，实现 Cache-Aside 草稿缓存策略。
 */
@Slf4j
@Primary
@Service
public class CacheAsideDraftService implements DraftService {

    private final DraftService delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public CacheAsideDraftService(
            RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties,
            @Qualifier("draftServiceImpl") DraftService delegate) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public void saveDraft(Long postId, Long userId, String content, boolean isAutoSave) {
        delegate.saveDraft(postId, userId, content, isAutoSave);

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
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (CacheConstants.isNullMarker(cached)) {
                    log.debug("Cache hit (null value) for draft: key={}", key);
                    return Optional.empty();
                }
                log.debug("Cache hit for draft: key={}", key);
                return Optional.of((DraftSnapshot) cached);
            }

            log.debug("Cache miss for draft: key={}", key);
            Optional<DraftSnapshot> draft = delegate.getLatestDraft(postId, userId);
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
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (CacheConstants.isNullMarker(cached)) {
                    log.debug("Cache hit (empty list) for user drafts: key={}", key);
                    return List.of();
                }
                log.debug("Cache hit for user drafts: key={}", key);
                return (List<DraftSnapshot>) cached;
            }

            log.debug("Cache miss for user drafts: key={}", key);
            List<DraftSnapshot> drafts = delegate.getUserDrafts(userId);
            cacheDraftList(key, drafts);
            return drafts;
        } catch (Exception e) {
            log.warn("Cache lookup failed for user drafts, falling back to database: {}", e.getMessage());
            return delegate.getUserDrafts(userId);
        }
    }

    @Override
    public void deleteDraft(Long postId, Long userId) {
        delegate.deleteDraft(postId, userId);

        try {
            evictDraftCache(postId, userId);
        } catch (Exception e) {
            log.warn("Failed to evict draft cache after delete: {}", e.getMessage());
        }
    }

    @Override
    public long cleanExpiredDrafts(int expireDays) {
        return delegate.cleanExpiredDrafts(expireDays);
    }

    public void warmUpDraftCache(Long postId, Long userId) {
        try {
            Optional<DraftSnapshot> draft = delegate.getLatestDraft(postId, userId);
            draft.ifPresent(value -> cacheDraft(PostRedisKeys.draft(postId, userId), value));
            log.debug("Warmed up draft cache for postId={}, userId={}", postId, userId);
        } catch (Exception e) {
            log.warn("Failed to warm up draft cache for postId={}, userId={}: {}",
                    postId, userId, e.getMessage());
        }
    }

    public void warmUpUserDraftsCache(Long userId) {
        try {
            List<DraftSnapshot> drafts = delegate.getUserDrafts(userId);
            cacheDraftList(PostRedisKeys.userDrafts(userId), drafts);
            log.debug("Warmed up user drafts cache for userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to warm up user drafts cache for userId={}: {}", userId, e.getMessage());
        }
    }

    private void cacheDraft(String key, DraftSnapshot draft) {
        if (draft != null) {
            long ttlWithJitter = cacheProperties.getDraft().getTtl() + randomJitter();
            redisTemplate.opsForValue().set(key, draft, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached draft: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            redisTemplate.opsForValue().set(
                    key,
                    CacheConstants.NULL_MARKER,
                    CacheConstants.NULL_VALUE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Cached null value for draft: key={}", key);
        }
    }

    private void cacheDraftList(String key, List<DraftSnapshot> drafts) {
        if (drafts != null && !drafts.isEmpty()) {
            long ttlWithJitter = cacheProperties.getDraft().getListTtl() + randomJitter();
            redisTemplate.opsForValue().set(key, drafts, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached draft list: key={}, size={}, ttl={}s", key, drafts.size(), ttlWithJitter);
        } else {
            redisTemplate.opsForValue().set(
                    key,
                    CacheConstants.NULL_MARKER,
                    CacheConstants.NULL_VALUE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Cached empty draft list: key={}", key);
        }
    }

    private void evictDraftCache(Long postId, Long userId) {
        redisTemplate.delete(PostRedisKeys.draft(postId, userId));
        redisTemplate.delete(PostRedisKeys.userDrafts(userId));
        log.debug("Evicted draft cache for postId={}, userId={}", postId, userId);
    }

    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, cacheProperties.getJitter().getMaxSeconds());
    }
}
