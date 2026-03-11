package com.zhicore.content.application.decorator;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.application.port.store.DraftCacheStore;
import com.zhicore.content.domain.service.DraftQueryService;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 草稿查询缓存装饰器。
 *
 * 装饰 DraftQueryService，实现 Cache-Aside 草稿查询缓存策略。
 */
@Slf4j
@Primary
@Service
public class CacheAsideDraftQuery implements DraftQueryService {

    private final DraftQueryService delegate;
    private final DraftCacheStore draftCacheStore;
    private final CacheProperties cacheProperties;

    public CacheAsideDraftQuery(
            DraftCacheStore draftCacheStore,
            CacheProperties cacheProperties,
            @Qualifier("draftServiceImpl") DraftQueryService delegate) {
        this.draftCacheStore = draftCacheStore;
        this.cacheProperties = cacheProperties;
        this.delegate = delegate;
    }

    @Override
    public Optional<DraftSnapshot> getLatestDraft(Long postId, Long userId) {
        try {
            CacheResult<DraftSnapshot> cached = draftCacheStore.getLatestDraft(postId, userId);
            if (cached.isNull()) {
                log.debug("Cache hit (null value) for draft: postId={}, userId={}", postId, userId);
                return Optional.empty();
            }
            if (cached.isHit()) {
                log.debug("Cache hit for draft: postId={}, userId={}", postId, userId);
                return Optional.of(cached.getValue());
            }

            log.debug("Cache miss for draft: postId={}, userId={}", postId, userId);
            Optional<DraftSnapshot> draft = delegate.getLatestDraft(postId, userId);
            cacheDraft(postId, userId, draft.orElse(null));
            return draft;
        } catch (Exception e) {
            log.warn("Cache lookup failed for draft, falling back to database: {}", e.getMessage());
            return delegate.getLatestDraft(postId, userId);
        }
    }

    @Override
    public List<DraftSnapshot> getUserDrafts(Long userId) {
        try {
            CacheResult<List<DraftSnapshot>> cached = draftCacheStore.getUserDrafts(userId);
            if (cached.isNull()) {
                log.debug("Cache hit (empty list) for user drafts: userId={}", userId);
                return List.of();
            }
            if (cached.isHit()) {
                log.debug("Cache hit for user drafts: userId={}", userId);
                return cached.getValue();
            }

            log.debug("Cache miss for user drafts: userId={}", userId);
            List<DraftSnapshot> drafts = delegate.getUserDrafts(userId);
            cacheDraftList(userId, drafts);
            return drafts;
        } catch (Exception e) {
            log.warn("Cache lookup failed for user drafts, falling back to database: {}", e.getMessage());
            return delegate.getUserDrafts(userId);
        }
    }

    public void warmUpDraftCache(Long postId, Long userId) {
        try {
            Optional<DraftSnapshot> draft = delegate.getLatestDraft(postId, userId);
            draft.ifPresent(value -> cacheDraft(postId, userId, value));
            log.debug("Warmed up draft cache for postId={}, userId={}", postId, userId);
        } catch (Exception e) {
            log.warn("Failed to warm up draft cache for postId={}, userId={}: {}",
                    postId, userId, e.getMessage());
        }
    }

    public void warmUpUserDraftsCache(Long userId) {
        try {
            List<DraftSnapshot> drafts = delegate.getUserDrafts(userId);
            cacheDraftList(userId, drafts);
            log.debug("Warmed up user drafts cache for userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to warm up user drafts cache for userId={}: {}", userId, e.getMessage());
        }
    }

    private void cacheDraft(Long postId, Long userId, DraftSnapshot draft) {
        if (draft != null) {
            Duration ttl = Duration.ofSeconds(cacheProperties.getDraft().getTtl() + randomJitter());
            draftCacheStore.setLatestDraft(postId, userId, draft, ttl);
            log.debug("Cached draft: postId={}, userId={}, ttl={}s", postId, userId, ttl.getSeconds());
        } else {
            draftCacheStore.setLatestDraftNull(
                    postId,
                    userId,
                    Duration.ofSeconds(CacheConstants.NULL_VALUE_TTL_SECONDS)
            );
            log.debug("Cached null value for draft: postId={}, userId={}", postId, userId);
        }
    }

    private void cacheDraftList(Long userId, List<DraftSnapshot> drafts) {
        if (drafts != null && !drafts.isEmpty()) {
            Duration ttl = Duration.ofSeconds(cacheProperties.getDraft().getListTtl() + randomJitter());
            draftCacheStore.setUserDrafts(userId, drafts, ttl);
            log.debug("Cached draft list: userId={}, size={}, ttl={}s", userId, drafts.size(), ttl.getSeconds());
        } else {
            draftCacheStore.setUserDraftsEmpty(userId, Duration.ofSeconds(CacheConstants.NULL_VALUE_TTL_SECONDS));
            log.debug("Cached empty draft list: userId={}", userId);
        }
    }

    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, cacheProperties.getJitter().getMaxSeconds());
    }
}
