package com.zhicore.ranking.application.service;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.ServiceUnavailableException;
import com.zhicore.ranking.application.model.RankingLedgerEventRecord;
import com.zhicore.ranking.infrastructure.config.RankingPipelineProperties;
import com.zhicore.ranking.infrastructure.pg.PgRankingLedgerRepository;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 ledger 的全量补算服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingLedgerReplayService {

    private static final Duration REPLAY_LOCK_WAIT_TIME = Duration.ZERO;

    private final PgRankingLedgerRepository repository;
    private final RankingPipelineProperties pipelineProperties;
    private final RankingLedgerFlushService flushService;
    private final RankingSnapshotService snapshotService;
    private final RankingReplayBarrierService replayBarrierService;
    private final LockManager lockManager;
    private final RankingHotPostCandidateService rankingHotPostCandidateService;

    public int rebuildFromLedger() {
        List<String> acquiredLocks = acquireReplayLocks();
        try {
            replayBarrierService.awaitInFlightIngestionDrain();
            resetMaterializedState();

            int replayed = replayLedgerIntoBuckets();
            int flushedBuckets = flushAllBucketsWithoutRedis();
            snapshotService.refreshActiveSnapshots();
            refreshHotCandidatesAfterReplay();

            log.info("ranking ledger 全量补算完成: replayedEvents={}, flushedBuckets={}", replayed, flushedBuckets);
            return replayed;
        } finally {
            releaseReplayLocks(acquiredLocks);
        }
    }

    protected void resetMaterializedState() {
        repository.resetMaterializedState();
    }

    private int replayLedgerIntoBuckets() {
        int batchSize = Math.max(pipelineProperties.getFlushBatchSize(), 200);
        OffsetDateTime cursorOccurredAt = null;
        String cursorEventId = null;
        int replayed = 0;

        while (true) {
            List<RankingLedgerEventRecord> batch = repository.listLedgerEventsAfter(cursorOccurredAt, cursorEventId, batchSize);
            if (batch.isEmpty()) {
                return replayed;
            }
            replayBatch(batch);
            replayed += batch.size();

            RankingLedgerEventRecord last = batch.get(batch.size() - 1);
            cursorOccurredAt = last.getOccurredAt();
            cursorEventId = last.getEventId();
        }
    }

    protected void replayBatch(List<RankingLedgerEventRecord> batch) {
        for (RankingLedgerEventRecord event : batch) {
            repository.accumulateBucket(event, floorBucketStart(event.getOccurredAt()));
        }
    }

    private int flushAllBucketsWithoutRedis() {
        int total = 0;
        while (true) {
            int flushed = flushService.flushPendingBuckets(false);
            if (flushed <= 0) {
                return total;
            }
            total += flushed;
        }
    }

    private OffsetDateTime floorBucketStart(OffsetDateTime occurredAt) {
        long bucketWindowSeconds = pipelineProperties.getBucketWindowSeconds();
        long epochSecond = occurredAt.toEpochSecond();
        long floored = (epochSecond / bucketWindowSeconds) * bucketWindowSeconds;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(floored), ZoneId.systemDefault());
    }

    private List<String> acquireReplayLocks() {
        List<String> acquired = new ArrayList<>(3);
        for (String lockKey : replayLockKeys()) {
            if (!lockManager.tryLockWithWatchdog(lockKey, REPLAY_LOCK_WAIT_TIME)) {
                releaseReplayLocks(acquired);
                throw new ServiceUnavailableException("排行榜补算任务正在执行，请稍后重试");
            }
            acquired.add(lockKey);
        }
        return acquired;
    }

    private void releaseReplayLocks(List<String> acquiredLocks) {
        for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
            lockManager.unlock(acquiredLocks.get(i));
        }
    }

    private List<String> replayLockKeys() {
        return List.of(
                RankingRedisKeys.replayLock(),
                RankingRedisKeys.schedulerLock("ranking-ledger-flush"),
                RankingRedisKeys.schedulerLock("ranking-snapshot-refresh")
        );
    }

    private void refreshHotCandidatesAfterReplay() {
        try {
            rankingHotPostCandidateService.refreshCandidates();
        } catch (Exception e) {
            log.warn("ranking ledger 全量补算后刷新热门文章候选集失败，保留旧结果", e);
        }
    }
}
