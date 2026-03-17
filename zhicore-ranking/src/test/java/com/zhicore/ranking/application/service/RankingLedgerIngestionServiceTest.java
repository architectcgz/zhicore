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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingLedgerIngestionService Tests")
class RankingLedgerIngestionServiceTest {

    @Mock
    private PgRankingLedgerRepository repository;

    @Mock
    private LockManager lockManager;

    private RankingLedgerIngestionService service;

    @BeforeEach
    void setUp() {
        RankingPipelineProperties properties = new RankingPipelineProperties();
        properties.setBucketWindowSeconds(10);
        service = new RankingLedgerIngestionService(repository, properties, lockManager);
    }

    @Test
    @DisplayName("saveEvent should persist normalized ledger event and bucket start")
    void saveEventShouldPersistNormalizedLedgerEvent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 8, 11, 30);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 3, 1, 8, 0);
        when(repository.saveEventAndAccumulateBucket(any(RankingLedgerEventRecord.class), any(LocalDateTime.class)))
                .thenReturn(true);
        when(lockManager.isLocked(RankingRedisKeys.replayLock())).thenReturn(false);

        boolean saved = service.saveEvent(
                "evt-1",
                "PostLikedIntegrationEvent",
                1001L,
                2002L,
                3003L,
                RankingMetricType.LIKE,
                1,
                occurredAt,
                publishedAt
        );

        assertTrue(saved);

        ArgumentCaptor<RankingLedgerEventRecord> eventCaptor = ArgumentCaptor.forClass(RankingLedgerEventRecord.class);
        ArgumentCaptor<LocalDateTime> bucketCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).acquireReplayBarrierSharedLock();
        verify(repository).saveEventAndAccumulateBucket(eventCaptor.capture(), bucketCaptor.capture());
        RankingLedgerEventRecord event = eventCaptor.getValue();
        assertEquals("evt-1", event.getEventId());
        assertEquals("PostLikedIntegrationEvent", event.getEventType());
        assertEquals(1001L, event.getPostId());
        assertEquals(2002L, event.getActorId());
        assertEquals(3003L, event.getAuthorId());
        assertEquals(RankingMetricType.LIKE, event.getMetricType());
        assertEquals(1, event.getDelta());
        assertEquals(occurredAt, event.getOccurredAt());
        assertEquals(publishedAt, event.getPublishedAt());
        assertEquals("1001", event.getPartitionKey());
        assertEquals(LocalDateTime.of(2026, 3, 8, 11, 30), bucketCaptor.getValue());
    }

    @Test
    @DisplayName("saveEvent should treat duplicate key as already accepted")
    void saveEventShouldTreatDuplicateKeyAsAccepted() {
        when(repository.saveEventAndAccumulateBucket(any(RankingLedgerEventRecord.class), any(LocalDateTime.class)))
                .thenReturn(false);
        when(lockManager.isLocked(RankingRedisKeys.replayLock())).thenReturn(false);

        boolean saved = service.saveEvent(
                "evt-duplicate",
                "PostViewedEvent",
                1001L,
                2002L,
                3003L,
                RankingMetricType.VIEW,
                1,
                LocalDateTime.now(),
                null
        );

        assertFalse(saved);
    }

    @Test
    @DisplayName("saveEvent should reject writes while replay lock is held")
    void saveEventShouldRejectDuringReplay() {
        when(lockManager.isLocked(RankingRedisKeys.replayLock())).thenReturn(true);

        assertThrows(ServiceUnavailableException.class, () -> service.saveEvent(
                "evt-replay",
                "PostViewedEvent",
                1001L,
                2002L,
                3003L,
                RankingMetricType.VIEW,
                1,
                LocalDateTime.now(),
                null
        ));

        verify(repository).acquireReplayBarrierSharedLock();
        verify(repository, never()).saveEventAndAccumulateBucket(any(RankingLedgerEventRecord.class), any(LocalDateTime.class));
    }
}
