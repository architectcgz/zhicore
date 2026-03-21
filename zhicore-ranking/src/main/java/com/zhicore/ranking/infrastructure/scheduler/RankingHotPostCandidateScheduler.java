package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.RankingHotPostCandidateService;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热门文章候选集刷新调度器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ranking.candidate.posts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RankingHotPostCandidateScheduler {

    private final RankingHotPostCandidateService candidateService;
    private final DistributedLockExecutor lockExecutor;

    @Scheduled(fixedDelayString = "${ranking.candidate.posts.refresh-interval:60000}")
    public void refresh() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("ranking-hot-post-candidates-refresh"), () -> {
            try {
                candidateService.refreshCandidates();
                log.debug("ranking 热门文章候选集刷新完成");
            } catch (Exception e) {
                log.error("ranking 热门文章候选集刷新失败，保留旧结果", e);
            }
        });
    }
}
