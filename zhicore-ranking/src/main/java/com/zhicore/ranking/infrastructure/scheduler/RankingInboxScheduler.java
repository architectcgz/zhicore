package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.RankingInboxAggregationService;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时聚合 ranking inbox 事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingInboxScheduler {

    private final RankingInboxAggregationService aggregationService;
    private final DistributedLockExecutor lockExecutor;

    @Scheduled(fixedDelayString = "${ranking.inbox.scan-interval:5000}")
    public void aggregate() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("ranking-inbox-aggregate"), () -> {
            int processed = aggregationService.aggregatePendingEvents();
            if (processed > 0) {
                log.info("ranking inbox 聚合完成: processed={}", processed);
            }
        });
    }
}
