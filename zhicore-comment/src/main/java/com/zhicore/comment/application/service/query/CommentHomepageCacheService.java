package com.zhicore.comment.application.service.query;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.port.store.CommentHomepageCacheStore;
import com.zhicore.comment.application.port.store.RankingHotPostCandidateStore;
import com.zhicore.comment.infrastructure.config.CommentHomepageCacheProperties;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 首页评论快照缓存服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentHomepageCacheService {

    private static final String ENTITY_TYPE_POST = "post";

    private final CommentHomepageCacheStore commentHomepageCacheStore;
    private final RankingHotPostCandidateStore rankingHotPostCandidateStore;
    private final HotDataIdentifier hotDataIdentifier;
    private final CommentHomepageCacheProperties properties;

    public PageResult<CommentVO> getTopLevelCommentsPage(Long postId,
                                                         int page,
                                                         int size,
                                                         CommentSortType sortType,
                                                         int hotRepliesLimit,
                                                         Supplier<PageResult<CommentVO>> loader) {
        recordPostAccess(postId);
        if (!isHomepageRequest(page, sortType)) {
            return loader.get();
        }
        if (!shouldEnableHomepageCache(postId)) {
            return loader.get();
        }

        try {
            return commentHomepageCacheStore.get(postId, sortType, size, hotRepliesLimit)
                    .orElseGet(() -> loadAndCache(postId, size, sortType, hotRepliesLimit, loader));
        } catch (Exception e) {
            log.warn("读取首页评论缓存失败，回退到实时组装: postId={}, sort={}, error={}",
                    postId, sortType, e.getMessage());
            return loader.get();
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

    private void recordPostAccess(Long postId) {
        try {
            hotDataIdentifier.recordAccess(ENTITY_TYPE_POST, postId);
        } catch (Exception e) {
            log.warn("记录评论本地访问热度失败: postId={}, error={}", postId, e.getMessage());
        }
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
