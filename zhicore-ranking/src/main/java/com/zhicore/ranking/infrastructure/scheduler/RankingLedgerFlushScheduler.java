package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.RankingLedgerFlushService;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时 flush ranking bucket。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ranking.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RankingLedgerFlushScheduler {

    private final RankingLedgerFlushService flushService;
    private final DistributedLockExecutor lockExecutor;

    @Scheduled(fixedDelayString = "${ranking.pipeline.flush-interval:5000}")
    public void flush() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("ranking-ledger-flush"), () -> {
            int flushed = flushService.flushPendingBuckets();
            if (flushed > 0) {
                log.debug("ranking ledger flush 完成: flushedBuckets={}", flushed);
            }
        });
    }
}
