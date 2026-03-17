package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.SnapshotPeriodScore;
import com.zhicore.ranking.application.model.SnapshotPostHotState;
import com.zhicore.ranking.application.port.policy.RankingSnapshotPolicy;
import com.zhicore.ranking.application.port.store.RankingSnapshotCacheStore;
import com.zhicore.ranking.application.port.store.RankingSnapshotSourceStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingSnapshotService Tests")
class RankingSnapshotServiceTest {

    @Mock
    private RankingSnapshotSourceStore snapshotSourceStore;

    @Mock
    private RankingSnapshotCacheStore snapshotCacheStore;

    @Mock
    private RankingSnapshotPolicy snapshotPolicy;

    private RankingSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setView(1.0);
        weightProperties.setLike(5.0);
        weightProperties.setComment(10.0);
        weightProperties.setFavorite(8.0);
        weightProperties.setHalfLifeDays(7.0);
        when(snapshotPolicy.topSize()).thenReturn(100);

        snapshotService = new RankingSnapshotService(
                snapshotSourceStore,
                snapshotCacheStore,
                snapshotPolicy,
                new HotScoreCalculator(weightProperties)
        );
    }

    @Test
    @DisplayName("从权威状态和周期分数刷新 Redis 当前快照")
    void refreshCurrentSnapshots_shouldReplaceHotAndPeriodRankings() {
        LocalDateTime publishedAt = LocalDateTime.now();
        SnapshotPostHotState post1001 = SnapshotPostHotState.builder()
                .postId(1001L)
                .authorId(2001L)
                .topicIds(List.of(3001L))
                .publishedAt(publishedAt)
                .viewCount(10L)
                .likeCount(2)
                .favoriteCount(1)
                .commentCount(0)
                .build();
        SnapshotPostHotState post1002 = SnapshotPostHotState.builder()
                .postId(1002L)
                .authorId(2001L)
                .topicIds(List.of(3002L))
                .publishedAt(publishedAt)
                .viewCount(4L)
                .likeCount(1)
                .favoriteCount(0)
                .commentCount(1)
                .build();

        when(snapshotSourceStore.listActivePostStates()).thenReturn(List.of(post1001, post1002));
        when(snapshotSourceStore.listPeriodScores("DAY", LocalDate.now().toString()))
                .thenReturn(
                        List.of(
                                score(1001L, 7.0),
                                score(1002L, 10.0)
                        ));
        LocalDate today = LocalDate.now();
        int currentWeekBasedYear = com.zhicore.ranking.infrastructure.redis.RankingRedisKeys.getWeekBasedYear(today);
        int currentWeekNumber = com.zhicore.ranking.infrastructure.redis.RankingRedisKeys.getWeekNumber(today);
        String weekKey = "%d-W%02d".formatted(currentWeekBasedYear, currentWeekNumber);
        when(snapshotSourceStore.listPeriodScores("WEEK", weekKey))
                .thenReturn(List.of(score(1002L, 10.0)));
        when(snapshotSourceStore.listPeriodScores("MONTH", "%d-%02d".formatted(today.getYear(), today.getMonthValue())))
                .thenReturn(List.of(score(1001L, 8.0)));

        snapshotService.refreshCurrentSnapshots();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> totalPostsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> totalCreatorsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> totalTopicsCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotCacheStore).replaceTotalRanking(
                totalPostsCaptor.capture(),
                totalCreatorsCaptor.capture(),
                totalTopicsCaptor.capture()
        );
        List<HotScore> totalPosts = totalPostsCaptor.getValue();
        assertEquals(List.of("1001", "1002"), totalPosts.stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(28.0, 19.0), totalPosts.stream().map(HotScore::getScore).toList());
        assertEquals(1, totalCreatorsCaptor.getValue().size());
        assertEquals("2001", totalCreatorsCaptor.getValue().get(0).getEntityId());
        assertEquals(47.0, totalCreatorsCaptor.getValue().get(0).getScore());
        assertEquals(List.of("3001", "3002"), totalTopicsCaptor.getValue().stream().map(HotScore::getEntityId).toList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> dailyPostsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> dailyCreatorsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> dailyTopicsCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotCacheStore).replaceDailyRanking(
                org.mockito.ArgumentMatchers.eq(LocalDate.now()),
                dailyPostsCaptor.capture(),
                dailyCreatorsCaptor.capture(),
                dailyTopicsCaptor.capture()
        );
        assertEquals(List.of("1002", "1001"), dailyPostsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(10.0, 7.0), dailyPostsCaptor.getValue().stream().map(HotScore::getScore).toList());
        assertEquals(List.of("2001"), dailyCreatorsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of("3002", "3001"), dailyTopicsCaptor.getValue().stream().map(HotScore::getEntityId).toList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> weeklyPostsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> weeklyCreatorsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> weeklyTopicsCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotCacheStore).replaceWeeklyRanking(
                org.mockito.ArgumentMatchers.eq(currentWeekBasedYear),
                org.mockito.ArgumentMatchers.eq(currentWeekNumber),
                weeklyPostsCaptor.capture(),
                weeklyCreatorsCaptor.capture(),
                weeklyTopicsCaptor.capture()
        );
        assertEquals(List.of("1002"), weeklyPostsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(10.0), weeklyPostsCaptor.getValue().stream().map(HotScore::getScore).toList());
        assertEquals(List.of("2001"), weeklyCreatorsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of("3002"), weeklyTopicsCaptor.getValue().stream().map(HotScore::getEntityId).toList());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> monthlyPostsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> monthlyCreatorsCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HotScore>> monthlyTopicsCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotCacheStore).replaceMonthlyRanking(
                org.mockito.ArgumentMatchers.eq(today.getYear()),
                org.mockito.ArgumentMatchers.eq(today.getMonthValue()),
                monthlyPostsCaptor.capture(),
                monthlyCreatorsCaptor.capture(),
                monthlyTopicsCaptor.capture()
        );
        assertEquals(List.of("1001"), monthlyPostsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(8.0), monthlyPostsCaptor.getValue().stream().map(HotScore::getScore).toList());
        assertEquals(List.of("2001"), monthlyCreatorsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of("3001"), monthlyTopicsCaptor.getValue().stream().map(HotScore::getEntityId).toList());
    }

    @Test
    @DisplayName("refreshActiveSnapshots should rebuild the active Redis recovery window")
    void refreshActiveSnapshotsShouldCoverRecoveryWindow() {
        SnapshotPostHotState post = SnapshotPostHotState.builder()
                .postId(1001L)
                .authorId(2001L)
                .topicIds(List.of(3001L))
                .publishedAt(LocalDateTime.now())
                .viewCount(10L)
                .likeCount(1)
                .favoriteCount(0)
                .commentCount(0)
                .build();
        when(snapshotSourceStore.listActivePostStates()).thenReturn(List.of(post));

        LocalDate today = LocalDate.now();
        for (int daysAgo = 0; daysAgo <= 2; daysAgo++) {
            LocalDate date = today.minusDays(daysAgo);
            when(snapshotSourceStore.listPeriodScores("DAY", date.toString()))
                    .thenReturn(List.of(score(1001L, 5.0 + daysAgo)));
        }

        snapshotService.refreshActiveSnapshots();

        verify(snapshotCacheStore).replaceDailyRanking(
                org.mockito.ArgumentMatchers.eq(today.minusDays(2)),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any()
        );
        verify(snapshotCacheStore).replaceDailyRanking(
                org.mockito.ArgumentMatchers.eq(today.minusDays(1)),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any()
        );
        verify(snapshotCacheStore).replaceDailyRanking(
                org.mockito.ArgumentMatchers.eq(today),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any()
        );
        verify(snapshotCacheStore, atLeastOnce()).replaceWeeklyRanking(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any(),
                org.mockito.ArgumentMatchers.<List<HotScore>>any()
        );
    }

    private SnapshotPeriodScore score(Long postId, double deltaScore) {
        return SnapshotPeriodScore.builder()
                .postId(postId)
                .deltaScore(deltaScore)
                .build();
    }
}
