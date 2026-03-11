package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRankingMaintenanceStore Tests")
class RedisRankingMaintenanceStoreTest {

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @Test
    @DisplayName("清理过期文章榜时应删除过期日榜和周榜")
    void cleanupExpiredHotPostRankings_shouldDeleteExpectedKeys() {
        RedisRankingMaintenanceStore store = new RedisRankingMaintenanceStore(rankingRedisRepository);
        LocalDate referenceDate = LocalDate.of(2026, 3, 11);

        store.cleanupExpiredHotPostRankings(referenceDate);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rankingRedisRepository, org.mockito.Mockito.times(14)).deleteKey(keyCaptor.capture());

        List<String> capturedKeys = keyCaptor.getAllValues();
        for (int i = 0; i < 7; i++) {
            LocalDate date = referenceDate.minusDays(3L + i);
            assertEquals(RankingRedisKeys.dailyPosts(date), capturedKeys.get(i));
        }

        int currentWeek = referenceDate.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
        for (int i = 0; i < 7; i++) {
            assertEquals(RankingRedisKeys.weeklyPosts(currentWeek - 3 - i), capturedKeys.get(7 + i));
        }
    }

    @Test
    @DisplayName("清理过期创作者榜时应删除过期日榜")
    void cleanupExpiredCreatorDailyRankings_shouldDeleteExpectedKeys() {
        RedisRankingMaintenanceStore store = new RedisRankingMaintenanceStore(rankingRedisRepository);
        LocalDate referenceDate = LocalDate.of(2026, 3, 11);

        store.cleanupExpiredCreatorDailyRankings(referenceDate);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rankingRedisRepository, org.mockito.Mockito.times(7)).deleteKey(keyCaptor.capture());

        List<String> capturedKeys = keyCaptor.getAllValues();
        for (int i = 0; i < 7; i++) {
            LocalDate date = referenceDate.minusDays(3L + i);
            assertEquals(RankingRedisKeys.dailyCreators(date), capturedKeys.get(i));
        }
    }

    @Test
    @DisplayName("清理过期话题榜时应删除过期日榜")
    void cleanupExpiredTopicDailyRankings_shouldDeleteExpectedKeys() {
        RedisRankingMaintenanceStore store = new RedisRankingMaintenanceStore(rankingRedisRepository);
        LocalDate referenceDate = LocalDate.of(2026, 3, 11);

        store.cleanupExpiredTopicDailyRankings(referenceDate);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rankingRedisRepository, org.mockito.Mockito.times(7)).deleteKey(keyCaptor.capture());

        List<String> capturedKeys = keyCaptor.getAllValues();
        for (int i = 0; i < 7; i++) {
            LocalDate date = referenceDate.minusDays(3L + i);
            assertEquals(RankingRedisKeys.dailyTopics(date), capturedKeys.get(i));
        }
    }

    @Test
    @DisplayName("淘汰总榜时应裁剪三个总榜")
    void trimTotalBoards_shouldTrimAllBoards() {
        RedisRankingMaintenanceStore store = new RedisRankingMaintenanceStore(rankingRedisRepository);
        when(rankingRedisRepository.trimSortedSet(RankingRedisKeys.hotPosts(), 10000)).thenReturn(2L);
        when(rankingRedisRepository.trimSortedSet(RankingRedisKeys.hotCreators(), 10000)).thenReturn(1L);
        when(rankingRedisRepository.trimSortedSet(RankingRedisKeys.hotTopics(), 10000)).thenReturn(0L);

        store.trimTotalBoards(10000);

        verify(rankingRedisRepository).trimSortedSet(RankingRedisKeys.hotPosts(), 10000);
        verify(rankingRedisRepository).trimSortedSet(RankingRedisKeys.hotCreators(), 10000);
        verify(rankingRedisRepository).trimSortedSet(RankingRedisKeys.hotTopics(), 10000);
        verifyNoMoreInteractions(rankingRedisRepository);
    }
}
