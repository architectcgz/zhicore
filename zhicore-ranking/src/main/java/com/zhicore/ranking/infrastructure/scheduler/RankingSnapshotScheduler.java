package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.RankingSnapshotService;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时刷新当前 Redis 快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotScheduler {

    private final RankingSnapshotService snapshotService;
    private final DistributedLockExecutor lockExecutor;

    @Scheduled(fixedDelayString = "${ranking.snapshot.refresh-interval:60000}")
    public void refresh() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("ranking-snapshot-refresh"), () -> {
            snapshotService.refreshCurrentSnapshots();
            log.debug("ranking 当前快照刷新完成");
        });
    }
}
