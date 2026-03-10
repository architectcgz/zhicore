package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.AggregationInboxEvent;
import com.zhicore.ranking.application.model.AggregationPostHotState;
import com.zhicore.ranking.application.port.store.RankingInboxAggregationStore;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingInboxAggregationService Tests")
class RankingInboxAggregationServiceTest {

    @Mock
    private RankingInboxAggregationStore rankingInboxAggregationStore;

    @Mock
    private PostMetadataResolver postMetadataResolver;

    private RankingInboxAggregationService aggregationService;

    @BeforeEach
    void setUp() {
        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setView(1.0);
        weightProperties.setLike(5.0);
        weightProperties.setComment(10.0);
        weightProperties.setFavorite(8.0);
        weightProperties.setHalfLifeDays(7.0);
        when(rankingInboxAggregationStore.recentAppliedEventWindowSize()).thenReturn(16);

        aggregationService = new RankingInboxAggregationService(
                rankingInboxAggregationStore,
                postMetadataResolver,
                new HotScoreCalculator(weightProperties)
        );
    }

    @Test
    @DisplayName("聚合待处理 inbox 事件并更新文章热度权威状态")
    void aggregatePendingEvents_shouldPersistAuthorityState() {
        LocalDateTime now = LocalDateTime.now();
        AggregationInboxEvent viewEvent = AggregationInboxEvent.builder()
                .eventId("evt-view")
                .postId(1001L)
                .metricType(RankingMetricType.VIEW)
                .countDelta(3)
                .authorId(2001L)
                .occurredAt(now.minusSeconds(2))
                .publishedAt(now.minusDays(1))
                .retryCount(0)
                .build();
        AggregationInboxEvent likeEvent = AggregationInboxEvent.builder()
                .eventId("evt-like")
                .postId(1001L)
                .metricType(RankingMetricType.LIKE)
                .countDelta(2)
                .authorId(2001L)
                .occurredAt(now.minusSeconds(1))
                .publishedAt(now.minusDays(1))
                .retryCount(0)
                .build();

        when(rankingInboxAggregationStore.claimPendingEvents()).thenReturn(List.of(viewEvent, likeEvent));
        when(rankingInboxAggregationStore.findStatesByPostIds(anyCollection())).thenReturn(Map.of());
        when(postMetadataResolver.resolve(any(Collection.class))).thenReturn(Map.of(
                1001L,
                PostMetadataResolver.PostMetadata.builder()
                        .postId(1001L)
                        .authorId(2001L)
                        .publishedAt(now.minusDays(1))
                        .topicIds(List.of(3001L, 3002L))
                        .build()
        ));

        int processed = aggregationService.aggregatePendingEvents();

        assertEquals(2, processed);
        ArgumentCaptor<AggregationPostHotState> stateCaptor = ArgumentCaptor.forClass(AggregationPostHotState.class);
        verify(rankingInboxAggregationStore).saveState(stateCaptor.capture());

        AggregationPostHotState savedState = stateCaptor.getValue();
        assertNotNull(savedState);
        assertEquals(1001L, savedState.getPostId());
        assertEquals(2001L, savedState.getAuthorId());
        assertEquals(List.of(3001L, 3002L), savedState.getTopicIds());
        assertEquals(3L, savedState.getViewCount());
        assertEquals(2, savedState.getLikeCount());
        assertEquals(0, savedState.getCommentCount());
        assertEquals(0, savedState.getFavoriteCount());
        assertEquals(13.0, savedState.getRawScoreCache());
        assertTrue(savedState.getRecentAppliedEventIds().containsAll(List.of("evt-view", "evt-like")));
        assertEquals(likeEvent.getOccurredAt(), savedState.getLastEventAt());

        verify(rankingInboxAggregationStore).markDone(List.of(viewEvent, likeEvent));
        verify(rankingInboxAggregationStore).cleanupExpiredDoneEvents();
    }
}
