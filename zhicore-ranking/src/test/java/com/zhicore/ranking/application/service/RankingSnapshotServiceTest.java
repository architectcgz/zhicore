package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingSnapshotProperties;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInbox;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInboxRepository;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotState;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotStateRepository;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingSnapshotService Tests")
class RankingSnapshotServiceTest {

    @Mock
    private RankingPostHotStateRepository postHotStateRepository;

    @Mock
    private RankingEventInboxRepository inboxRepository;

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    private RankingSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        RankingSnapshotProperties snapshotProperties = new RankingSnapshotProperties();
        snapshotProperties.setTopSize(100);

        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setView(1.0);
        weightProperties.setLike(5.0);
        weightProperties.setComment(10.0);
        weightProperties.setFavorite(8.0);
        weightProperties.setHalfLifeDays(7.0);

        snapshotService = new RankingSnapshotService(
                postHotStateRepository,
                inboxRepository,
                rankingRedisRepository,
                snapshotProperties,
                new HotScoreCalculator(weightProperties)
        );
    }

    @Test
    @DisplayName("从权威状态和 DONE inbox 事件刷新 Redis 当前快照")
    void refreshCurrentSnapshots_shouldReplaceHotAndPeriodRankings() {
        LocalDateTime publishedAt = LocalDateTime.now();
        RankingPostHotState post1001 = RankingPostHotState.builder()
                .postId(1001L)
                .authorId(2001L)
                .topicIds(List.of(3001L))
                .publishedAt(publishedAt)
                .status("ACTIVE")
                .viewCount(10L)
                .likeCount(2)
                .favoriteCount(1)
                .commentCount(0)
                .build();
        RankingPostHotState post1002 = RankingPostHotState.builder()
                .postId(1002L)
                .authorId(2001L)
                .topicIds(List.of(3002L))
                .publishedAt(publishedAt)
                .status("ACTIVE")
                .viewCount(4L)
                .likeCount(1)
                .favoriteCount(0)
                .commentCount(1)
                .build();

        when(postHotStateRepository.findByStatus("ACTIVE")).thenReturn(List.of(post1001, post1002));
        when(inboxRepository.findByStatusAndOccurredAtBetweenOrderByOccurredAtAsc(
                eq(RankingEventInbox.InboxStatus.DONE), any(), any()))
                .thenReturn(
                        List.of(
                                doneEvent("d-view", 1001L, RankingMetricType.VIEW, 2, publishedAt),
                                doneEvent("d-like", 1001L, RankingMetricType.LIKE, 1, publishedAt),
                                doneEvent("d-comment", 1002L, RankingMetricType.COMMENT, 1, publishedAt)
                        ),
                        List.of(doneEvent("w-like", 1002L, RankingMetricType.LIKE, 2, publishedAt)),
                        List.of(doneEvent("m-favorite", 1001L, RankingMetricType.FAVORITE, 1, publishedAt))
                );

        snapshotService.refreshCurrentSnapshots();

        ArgumentCaptor<List<HotScore>> totalPostsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.hotPosts()), totalPostsCaptor.capture(), isNull());
        List<HotScore> totalPosts = totalPostsCaptor.getValue();
        assertEquals(List.of("1001", "1002"), totalPosts.stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(28.0, 19.0), totalPosts.stream().map(HotScore::getScore).toList());

        ArgumentCaptor<List<HotScore>> totalCreatorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.hotCreators()), totalCreatorsCaptor.capture(), isNull());
        assertEquals(1, totalCreatorsCaptor.getValue().size());
        assertEquals("2001", totalCreatorsCaptor.getValue().get(0).getEntityId());
        assertEquals(47.0, totalCreatorsCaptor.getValue().get(0).getScore());

        ArgumentCaptor<List<HotScore>> totalTopicsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.hotTopics()), totalTopicsCaptor.capture(), isNull());
        assertEquals(List.of("3001", "3002"), totalTopicsCaptor.getValue().stream().map(HotScore::getEntityId).toList());

        ArgumentCaptor<List<HotScore>> dailyPostsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.todayPosts()), dailyPostsCaptor.capture(), eq(Duration.ofDays(2)));
        assertEquals(List.of("1002", "1001"), dailyPostsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(10.0, 7.0), dailyPostsCaptor.getValue().stream().map(HotScore::getScore).toList());

        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.dailyCreators(LocalDate.now())), any(), eq(Duration.ofDays(2)));
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.dailyTopics(LocalDate.now())), any(), eq(Duration.ofDays(2)));

        ArgumentCaptor<List<HotScore>> weeklyPostsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.currentWeekPosts()), weeklyPostsCaptor.capture(), eq(Duration.ofDays(14)));
        assertEquals(List.of("1002"), weeklyPostsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(10.0), weeklyPostsCaptor.getValue().stream().map(HotScore::getScore).toList());

        ArgumentCaptor<List<HotScore>> monthlyPostsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingRedisRepository).replaceRanking(eq(RankingRedisKeys.currentMonthPosts()), monthlyPostsCaptor.capture(), eq(Duration.ofDays(365)));
        assertEquals(List.of("1001"), monthlyPostsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(8.0), monthlyPostsCaptor.getValue().stream().map(HotScore::getScore).toList());
    }

    private RankingEventInbox doneEvent(String eventId,
                                        Long postId,
                                        RankingMetricType metricType,
                                        int countDelta,
                                        LocalDateTime publishedAt) {
        return RankingEventInbox.builder()
                .eventId(eventId)
                .postId(postId)
                .metricType(metricType)
                .countDelta(countDelta)
                .publishedAt(publishedAt)
                .occurredAt(LocalDateTime.now())
                .status(RankingEventInbox.InboxStatus.DONE)
                .build();
    }
}
