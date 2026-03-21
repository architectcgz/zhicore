package com.zhicore.comment.infrastructure.scheduler;

import com.zhicore.comment.application.service.RankingHotPostCandidateSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ranking 热门文章候选集同步调度器。
 */
@Component
@RequiredArgsConstructor
public class RankingHotPostCandidateSyncScheduler {

    private final RankingHotPostCandidateSyncService rankingHotPostCandidateSyncService;

    @Scheduled(
            initialDelayString = "${comment.homepage-cache.ranking-sync-initial-delay-ms:5000}",
            fixedDelayString = "${comment.homepage-cache.ranking-sync-interval-ms:30000}"
    )
    public void syncHotPostCandidates() {
        rankingHotPostCandidateSyncService.refreshCandidates();
    }
}
