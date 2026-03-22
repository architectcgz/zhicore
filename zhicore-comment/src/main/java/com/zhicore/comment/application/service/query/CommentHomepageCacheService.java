package com.zhicore.comment.application.service.query;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.port.CommentCacheKeyResolver;
import com.zhicore.comment.application.port.store.CommentHomepageCacheStore;
import com.zhicore.comment.application.port.store.RankingHotPostCandidateStore;
import com.zhicore.comment.infrastructure.config.CommentHomepageCacheProperties;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * 首页评论快照缓存服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentHomepageCacheService {

    private static final String ENTITY_TYPE_POST = "post";
    private static final int ACCESS_RECORD_TRACKER_MAX_SIZE = 10_000;

    private final CommentHomepageCacheStore commentHomepageCacheStore;
    private final RankingHotPostCandidateStore rankingHotPostCandidateStore;
    private final HotDataIdentifier hotDataIdentifier;
    private final CommentHomepageCacheProperties properties;
    private final LockManager lockManager;
    private final CommentCacheKeyResolver commentCacheKeyResolver;
    private final CacheProperties cacheProperties;
    private final ConcurrentMap<Long, Long> lastAccessRecordAtMillis = new ConcurrentHashMap<>();

    public PageResult<CommentVO> getTopLevelCommentsPage(Long postId,
                                                         int page,
                                                         int size,
                                                         CommentSortType sortType,
                                                         int hotRepliesLimit,
                                                         Supplier<PageResult<CommentVO>> loader) {
        boolean homepageRequest = isHomepageRequest(page, sortType);
        if (!homepageRequest) {
            recordPostAccessIfNeeded(postId);
            return loader.get();
        }

        Optional<PageResult<CommentVO>> cachedSnapshot = getCachedSnapshot(postId, size, sortType, hotRepliesLimit, loader);
        if (cachedSnapshot.isPresent()) {
            recordPostAccessIfNeeded(postId);
            return cachedSnapshot.get();
        }

        recordPostAccessIfNeeded(postId);
        if (!shouldEnableHomepageCache(postId)) {
            return loader.get();
        }

        try {
            return loadAndCacheWithLock(postId, size, sortType, hotRepliesLimit, loader);
        } catch (Exception e) {
            log.warn("读取首页评论缓存失败，回退到实时组装: postId={}, sort={}, error={}",
                    postId, sortType, e.getMessage());
            return loader.get();
        }
    }

    private Optional<PageResult<CommentVO>> getCachedSnapshot(Long postId,
                                                              int size,
                                                              CommentSortType sortType,
                                                              int hotRepliesLimit,
                                                              Supplier<PageResult<CommentVO>> loader) {
        try {
            return commentHomepageCacheStore.get(postId, sortType, size, hotRepliesLimit);
        } catch (Exception e) {
            log.warn("读取首页评论缓存失败，回退到实时组装: postId={}, sort={}, error={}",
                    postId, sortType, e.getMessage());
            return Optional.of(loader.get());
        }
    }

    private PageResult<CommentVO> loadAndCacheWithLock(Long postId,
                                                       int size,
                                                       CommentSortType sortType,
                                                       int hotRepliesLimit,
                                                       Supplier<PageResult<CommentVO>> loader) {
        String lockKey = commentCacheKeyResolver.lockHomepageSnapshot(postId, sortType, size, hotRepliesLimit);
        boolean fair = cacheProperties.getLock().isFair();
        Duration waitTime = Duration.ZERO;
        Duration leaseTime = Duration.ofSeconds(cacheProperties.getLock().getLeaseTime());

        boolean acquired = lockManager.tryLock(lockKey, waitTime, leaseTime, fair);
        if (!acquired) {
            return waitForPeerBackfill(postId, size, sortType, hotRepliesLimit, loader);
        }

        try {
            return commentHomepageCacheStore.get(postId, sortType, size, hotRepliesLimit)
                    .orElseGet(() -> loadAndCache(postId, size, sortType, hotRepliesLimit, loader));
        } finally {
            if (lockManager.isHeldByCurrentThread(lockKey, fair)) {
                lockManager.unlock(lockKey, fair);
            }
        }
    }

    private PageResult<CommentVO> loadAndCache(Long postId,
                                               int size,
                                               CommentSortType sortType,
                                               int hotRepliesLimit,
                                               Supplier<PageResult<CommentVO>> loader) {
        PageResult<CommentVO> snapshot = loader.get();
        try {
            commentHomepageCacheStore.set(
                    postId,
                    sortType,
                    size,
                    hotRepliesLimit,
                    snapshot,
                    snapshotTtl()
            );
        } catch (Exception e) {
            log.warn("回填首页评论缓存失败: postId={}, sort={}, error={}", postId, sortType, e.getMessage());
        }
        return snapshot;
    }

    private PageResult<CommentVO> waitForPeerBackfill(Long postId,
                                                      int size,
                                                      CommentSortType sortType,
                                                      int hotRepliesLimit,
                                                      Supplier<PageResult<CommentVO>> loader) {
        Optional<PageResult<CommentVO>> snapshot = tryReadCachedSnapshot(postId, size, sortType, hotRepliesLimit);
        if (snapshot.isPresent()) {
            return snapshot.get();
        }

        long maxWaitMs = properties.getPeerBackfillMaxWaitMs();
        if (maxWaitMs <= 0) {
            return loader.get();
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxWaitMs);
        long pollIntervalMs = Math.max(1L, properties.getPeerBackfillPollIntervalMs());

        while (System.nanoTime() < deadlineNanos) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            long waitNanos = Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(pollIntervalMs));
            LockSupport.parkNanos(waitNanos);

            snapshot = tryReadCachedSnapshot(postId, size, sortType, hotRepliesLimit);
            if (snapshot.isPresent()) {
                return snapshot.get();
            }
        }

        return loader.get();
    }

    private Optional<PageResult<CommentVO>> tryReadCachedSnapshot(Long postId,
                                                                  int size,
                                                                  CommentSortType sortType,
                                                                  int hotRepliesLimit) {
        try {
            return commentHomepageCacheStore.get(postId, sortType, size, hotRepliesLimit);
        } catch (Exception e) {
            log.warn("读取首页评论缓存失败，回退到实时组装: postId={}, sort={}, error={}",
                    postId, sortType, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean shouldEnableHomepageCache(Long postId) {
        try {
            if (rankingHotPostCandidateStore.contains(postId)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("检查 ranking 热门候选集失败: postId={}, error={}", postId, e.getMessage());
        }
        try {
            return hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId);
        } catch (Exception e) {
            log.warn("检查评论本地访问热度失败: postId={}, error={}", postId, e.getMessage());
            return false;
        }
    }

    private void recordPostAccessIfNeeded(Long postId) {
        if (!shouldRecordAccess(postId)) {
            return;
        }
        try {
            hotDataIdentifier.recordAccess(ENTITY_TYPE_POST, postId);
        } catch (Exception e) {
            log.warn("记录评论本地访问热度失败: postId={}, error={}", postId, e.getMessage());
        }
    }

    private boolean shouldRecordAccess(Long postId) {
        long intervalMs = properties.getAccessRecordIntervalMs();
        if (intervalMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        AtomicBoolean shouldRecord = new AtomicBoolean(false);
        lastAccessRecordAtMillis.compute(postId, (key, lastRecordedAt) -> {
            if (lastRecordedAt == null || now - lastRecordedAt >= intervalMs) {
                shouldRecord.set(true);
                return now;
            }
            return lastRecordedAt;
        });

        cleanupExpiredAccessTrackers(now, intervalMs);
        return shouldRecord.get();
    }

    private void cleanupExpiredAccessTrackers(long now, long intervalMs) {
        if (lastAccessRecordAtMillis.size() <= ACCESS_RECORD_TRACKER_MAX_SIZE) {
            return;
        }

        long expireBefore = now - Math.max(intervalMs, 1000L) * 2;
        lastAccessRecordAtMillis.entrySet().removeIf(entry -> entry.getValue() < expireBefore);
    }

    private boolean isHomepageRequest(int page, CommentSortType sortType) {
        return page == 0 && (sortType == CommentSortType.TIME || sortType == CommentSortType.HOT);
    }

    private Duration snapshotTtl() {
        long ttl = properties.getTtlSeconds();
        int jitter = properties.getTtlJitterSeconds();
        long jitterValue = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1L);
        return Duration.ofSeconds(ttl + jitterValue);
    }
}
