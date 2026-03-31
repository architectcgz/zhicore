package com.zhicore.comment.infrastructure.scheduler;

import com.zhicore.comment.application.service.RankingHotPostCandidateSyncService;
import com.zhicore.common.cache.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ranking 热门文章候选集同步调度器。
 *
 * <p>使用看门狗分布式锁，避免多实例重复拉取 ranking 候选集并重复写入 Redis。</p>
 */
@Component
@RequiredArgsConstructor
public class RankingHotPostCandidateSyncScheduler {

    public static final String HOT_POST_CANDIDATE_SYNC_LOCK_KEY = "comment:ranking:hot-post-candidate-sync:lock";

    private final RankingHotPostCandidateSyncService rankingHotPostCandidateSyncService;
    private final DistributedLockExecutor distributedLockExecutor;

    @Scheduled(
            initialDelayString = "${comment.homepage-cache.ranking-sync-initial-delay-ms:5000}",
            fixedDelayString = "${comment.homepage-cache.ranking-sync-interval-ms:30000}"
    )
    public void syncHotPostCandidates() {
        distributedLockExecutor.executeWithWatchdogLock(
                HOT_POST_CANDIDATE_SYNC_LOCK_KEY,
                rankingHotPostCandidateSyncService::refreshCandidates
        );
    }
}
