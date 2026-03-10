package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.RankingInboxEventRecord;
import com.zhicore.ranking.application.port.store.RankingEventInboxStore;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingEventInboxService Tests")
class RankingEventInboxServiceTest {

    @Mock
    private RankingEventInboxStore rankingEventInboxStore;

    @Mock
    private HotScoreCalculator hotScoreCalculator;

    private RankingEventInboxService service;

    @BeforeEach
    void setUp() {
        service = new RankingEventInboxService(rankingEventInboxStore, hotScoreCalculator);
    }

    @Test
    @DisplayName("saveEvent should persist normalized inbox event")
    void saveEventShouldPersistNormalizedInboxEvent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 8, 11, 30);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 3, 1, 8, 0);
        when(hotScoreCalculator.getLikeDelta()).thenReturn(5.0);
        when(rankingEventInboxStore.saveNewEvent(any(RankingInboxEventRecord.class))).thenReturn(true);

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

        ArgumentCaptor<RankingInboxEventRecord> captor = ArgumentCaptor.forClass(RankingInboxEventRecord.class);
        verify(rankingEventInboxStore).saveNewEvent(captor.capture());
        RankingInboxEventRecord inbox = captor.getValue();
        assertEquals("evt-1", inbox.getEventId());
        assertEquals("PostLikedIntegrationEvent", inbox.getEventType());
        assertEquals(1001L, inbox.getPostId());
        assertEquals(2002L, inbox.getUserId());
        assertEquals(3003L, inbox.getAuthorId());
        assertEquals(RankingMetricType.LIKE, inbox.getMetricType());
        assertEquals(1, inbox.getCountDelta());
        assertEquals(5.0, inbox.getScoreDelta());
        assertEquals(occurredAt, inbox.getOccurredAt());
        assertEquals(publishedAt, inbox.getPublishedAt());
        assertEquals(0, inbox.getRetryCount());
    }

    @Test
    @DisplayName("saveEvent should treat duplicate key as already accepted")
    void saveEventShouldTreatDuplicateKeyAsAccepted() {
        when(hotScoreCalculator.getViewDelta()).thenReturn(1.0);
        when(rankingEventInboxStore.saveNewEvent(any(RankingInboxEventRecord.class))).thenReturn(false);

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
}
