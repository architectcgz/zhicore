package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInbox;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

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
    private RankingEventInboxRepository inboxRepository;

    @Mock
    private HotScoreCalculator hotScoreCalculator;

    private RankingEventInboxService service;

    @BeforeEach
    void setUp() {
        service = new RankingEventInboxService(inboxRepository, hotScoreCalculator);
    }

    @Test
    @DisplayName("saveEvent should persist normalized inbox event")
    void saveEventShouldPersistNormalizedInboxEvent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 8, 11, 30);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 3, 1, 8, 0);
        when(hotScoreCalculator.getLikeDelta()).thenReturn(5.0);

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

        ArgumentCaptor<RankingEventInbox> captor = ArgumentCaptor.forClass(RankingEventInbox.class);
        verify(inboxRepository).save(captor.capture());
        RankingEventInbox inbox = captor.getValue();
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
        assertEquals(RankingEventInbox.InboxStatus.NEW, inbox.getStatus());
        assertEquals(0, inbox.getRetryCount());
    }

    @Test
    @DisplayName("saveEvent should treat duplicate key as already accepted")
    void saveEventShouldTreatDuplicateKeyAsAccepted() {
        when(hotScoreCalculator.getViewDelta()).thenReturn(1.0);
        when(inboxRepository.save(any(RankingEventInbox.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

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
