package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingInboxProperties;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInbox;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInboxRepository;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotState;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

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
    private MongoTemplate mongoTemplate;

    @Mock
    private RankingEventInboxRepository inboxRepository;

    @Mock
    private RankingPostHotStateRepository postHotStateRepository;

    @Mock
    private PostMetadataResolver postMetadataResolver;

    private RankingInboxAggregationService aggregationService;

    @BeforeEach
    void setUp() {
        RankingInboxProperties inboxProperties = new RankingInboxProperties();
        inboxProperties.setBatchSize(4);
        inboxProperties.setLeaseSeconds(60);
        inboxProperties.setDoneRetentionDays(30);
        inboxProperties.setAppliedEventWindowSize(16);

        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setView(1.0);
        weightProperties.setLike(5.0);
        weightProperties.setComment(10.0);
        weightProperties.setFavorite(8.0);
        weightProperties.setHalfLifeDays(7.0);

        aggregationService = new RankingInboxAggregationService(
                mongoTemplate,
                inboxRepository,
                postHotStateRepository,
                inboxProperties,
                postMetadataResolver,
                new HotScoreCalculator(weightProperties)
        );
    }

    @Test
    @DisplayName("聚合待处理 inbox 事件并更新文章热度权威状态")
    void aggregatePendingEvents_shouldPersistAuthorityState() {
        LocalDateTime now = LocalDateTime.now();
        RankingEventInbox viewEvent = RankingEventInbox.builder()
                .eventId("evt-view")
                .postId(1001L)
                .metricType(RankingMetricType.VIEW)
                .countDelta(3)
                .authorId(2001L)
                .occurredAt(now.minusSeconds(2))
                .publishedAt(now.minusDays(1))
                .status(RankingEventInbox.InboxStatus.NEW)
                .retryCount(0)
                .build();
        RankingEventInbox likeEvent = RankingEventInbox.builder()
                .eventId("evt-like")
                .postId(1001L)
                .metricType(RankingMetricType.LIKE)
                .countDelta(2)
                .authorId(2001L)
                .occurredAt(now.minusSeconds(1))
                .publishedAt(now.minusDays(1))
                .status(RankingEventInbox.InboxStatus.NEW)
                .retryCount(0)
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class)))
                .thenReturn(viewEvent, likeEvent, null);
        when(postHotStateRepository.findAllById(anyCollection())).thenReturn(List.of());
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
        ArgumentCaptor<RankingPostHotState> stateCaptor = ArgumentCaptor.forClass(RankingPostHotState.class);
        verify(postHotStateRepository).save(stateCaptor.capture());

        RankingPostHotState savedState = stateCaptor.getValue();
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

        verify(mongoTemplate, times(2)).updateFirst(any(), any(), any(Class.class));
        verify(inboxRepository).deleteByStatusAndOccurredAtBefore(any(), any());
    }
}
