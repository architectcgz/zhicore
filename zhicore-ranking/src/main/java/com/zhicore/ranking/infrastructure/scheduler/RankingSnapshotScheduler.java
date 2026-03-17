package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.RankingSnapshotService;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时重建当前 Redis 快照。
 *
 * <p>flush 后的增量物化是主路径，这个任务只负责兜底纠偏和时间衰减重建。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ranking.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RankingSnapshotScheduler {

    private final RankingSnapshotService snapshotService;
    private final DistributedLockExecutor lockExecutor;

    @Scheduled(fixedDelayString = "${ranking.snapshot.refresh-interval:60000}")
    public void refresh() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("ranking-snapshot-refresh"), () -> {
            snapshotService.refreshCurrentSnapshots();
            log.debug("ranking Redis 快照兜底重建完成");
        });
    }
}
