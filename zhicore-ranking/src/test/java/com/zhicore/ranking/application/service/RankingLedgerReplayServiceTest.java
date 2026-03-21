package com.zhicore.ranking.application.service;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.ServiceUnavailableException;
import com.zhicore.ranking.application.model.RankingLedgerEventRecord;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.infrastructure.config.RankingPipelineProperties;
import com.zhicore.ranking.infrastructure.pg.PgRankingLedgerRepository;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingLedgerReplayService Tests")
class RankingLedgerReplayServiceTest {

    @Mock
    private PgRankingLedgerRepository repository;

    @Mock
    private RankingLedgerFlushService flushService;

    @Mock
    private RankingSnapshotService snapshotService;

    @Mock
    private RankingReplayBarrierService replayBarrierService;

    @Mock
    private LockManager lockManager;

    @Mock
    private RankingHotPostCandidateService rankingHotPostCandidateService;

    private RankingLedgerReplayService service;

    @BeforeEach
    void setUp() {
        RankingPipelineProperties properties = new RankingPipelineProperties();
        properties.setBucketWindowSeconds(10);
        properties.setFlushBatchSize(2);
        service = new RankingLedgerReplayService(
                repository,
                properties,
                flushService,
                snapshotService,
                replayBarrierService,
                lockManager,
                rankingHotPostCandidateService
        );
    }

    @Test
    @DisplayName("rebuildFromLedger should reset state, replay ledger, flush buckets, and rebuild active snapshots")
    void rebuildFromLedgerShouldReplayAndRefreshSnapshots() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 9, 0, 12);
        RankingLedgerEventRecord first = ledgerEvent("evt-1", 1001L, RankingMetricType.VIEW, 1, occurredAt);
        RankingLedgerEventRecord second = ledgerEvent("evt-2", 1001L, RankingMetricType.LIKE, 1, occurredAt.plusSeconds(3));
        RankingLedgerEventRecord third = ledgerEvent("evt-3", 1002L, RankingMetricType.COMMENT, 1, occurredAt.plusSeconds(18));

        doNothing().when(repository).resetMaterializedState();
        doNothing().when(replayBarrierService).awaitInFlightIngestionDrain();
        when(lockManager.tryLockWithWatchdog(RankingRedisKeys.replayLock(), java.time.Duration.ZERO)).thenReturn(true);
        when(lockManager.tryLockWithWatchdog(RankingRedisKeys.schedulerLock("ranking-ledger-flush"), java.time.Duration.ZERO)).thenReturn(true);
        when(lockManager.tryLockWithWatchdog(RankingRedisKeys.schedulerLock("ranking-snapshot-refresh"), java.time.Duration.ZERO)).thenReturn(true);
        when(repository.listLedgerEventsAfter(null, null, 200)).thenReturn(List.of(first, second));
        when(repository.listLedgerEventsAfter(second.getOccurredAt(), second.getEventId(), 200)).thenReturn(List.of(third));
        when(repository.listLedgerEventsAfter(third.getOccurredAt(), third.getEventId(), 200)).thenReturn(List.of());
        when(flushService.flushPendingBuckets(false)).thenReturn(2, 1, 0);

        int replayed = service.rebuildFromLedger();

        assertEquals(3, replayed);
        verify(repository).resetMaterializedState();
        verify(replayBarrierService).awaitInFlightIngestionDrain();
        verify(repository).accumulateBucket(eq(first), eq(LocalDateTime.of(2026, 3, 15, 9, 0, 10)));
        verify(repository).accumulateBucket(eq(second), eq(LocalDateTime.of(2026, 3, 15, 9, 0, 10)));
        verify(repository).accumulateBucket(eq(third), eq(LocalDateTime.of(2026, 3, 15, 9, 0, 30)));
        verify(flushService, times(3)).flushPendingBuckets(false);
        verify(snapshotService).refreshActiveSnapshots();
        verify(rankingHotPostCandidateService).refreshCandidates();
        verify(lockManager).unlock(RankingRedisKeys.schedulerLock("ranking-snapshot-refresh"));
        verify(lockManager).unlock(RankingRedisKeys.schedulerLock("ranking-ledger-flush"));
        verify(lockManager).unlock(RankingRedisKeys.replayLock());
    }

    @Test
    @DisplayName("rebuildFromLedger should fail fast when replay lock is unavailable")
    void rebuildFromLedgerShouldRejectConcurrentReplay() {
        when(lockManager.tryLockWithWatchdog(RankingRedisKeys.replayLock(), java.time.Duration.ZERO)).thenReturn(false);

        assertThrows(ServiceUnavailableException.class, () -> service.rebuildFromLedger());

        verify(replayBarrierService, never()).awaitInFlightIngestionDrain();
        verify(repository, never()).resetMaterializedState();
    }

    private RankingLedgerEventRecord ledgerEvent(String eventId,
                                                 Long postId,
                                                 RankingMetricType metricType,
                                                 int delta,
                                                 LocalDateTime occurredAt) {
        return RankingLedgerEventRecord.builder()
                .eventId(eventId)
                .eventType("test")
                .postId(postId)
                .metricType(metricType)
                .delta(delta)
                .occurredAt(occurredAt)
                .partitionKey(String.valueOf(postId))
                .createdAt(occurredAt)
                .build();
    }
}
