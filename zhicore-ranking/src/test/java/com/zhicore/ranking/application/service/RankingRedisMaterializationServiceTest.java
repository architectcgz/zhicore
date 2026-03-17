package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.RankingBucketRecord;
import com.zhicore.ranking.application.model.RankingPostStateRecord;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingRedisMaterializationService Tests")
class RankingRedisMaterializationServiceTest {

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @Test
    @DisplayName("applyFlush should materialize total and period rankings incrementally")
    void applyFlushShouldMaterializeIncrementally() {
        RankingRedisMaterializationService service = new RankingRedisMaterializationService(rankingRedisRepository);
        LocalDateTime bucketStart = LocalDateTime.of(2026, 3, 14, 10, 0);

        RankingBucketRecord bucket = RankingBucketRecord.builder()
                .bucketStart(bucketStart)
                .postId(1001L)
                .build();
        RankingPostStateRecord previous = RankingPostStateRecord.builder()
                .postId(1001L)
                .hotScore(20D)
                .build();
        RankingPostStateRecord current = RankingPostStateRecord.builder()
                .postId(1001L)
                .authorId(2001L)
                .topicIds(List.of(3001L, 3002L))
                .hotScore(28D)
                .build();

        when(rankingRedisRepository.incrementScore(RankingRedisKeys.hotCreators(), "2001", 8D)).thenReturn(18D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.hotTopics(), "3001", 8D)).thenReturn(9D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.hotTopics(), "3002", 8D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.dailyPosts(bucketStart.toLocalDate()), "1001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.dailyCreators(bucketStart.toLocalDate()), "2001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.dailyTopics(bucketStart.toLocalDate()), "3001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.dailyTopics(bucketStart.toLocalDate()), "3002", 7D)).thenReturn(7D);
        int weekBasedYear = RankingRedisKeys.getWeekBasedYear(bucketStart.toLocalDate());
        int weekNumber = RankingRedisKeys.getWeekNumber(bucketStart.toLocalDate());
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.weeklyPosts(weekBasedYear, weekNumber), "1001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.weeklyCreators(weekBasedYear, weekNumber), "2001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), "3001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), "3002", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.monthlyPosts(2026, 3), "1001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.monthlyCreators(2026, 3), "2001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.monthlyTopics(2026, 3), "3001", 7D)).thenReturn(7D);
        when(rankingRedisRepository.incrementScore(RankingRedisKeys.monthlyTopics(2026, 3), "3002", 7D)).thenReturn(7D);

        service.applyFlush(bucket, previous, current, 7D);

        verify(rankingRedisRepository).updatePostScore("1001", 28D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.hotCreators(), "2001", 8D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.hotTopics(), "3001", 8D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.hotTopics(), "3002", 8D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.dailyPosts(bucketStart.toLocalDate()), "1001", 7D);
        verify(rankingRedisRepository).setExpire(RankingRedisKeys.dailyPosts(bucketStart.toLocalDate()), Duration.ofDays(2));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.dailyCreators(bucketStart.toLocalDate()), "2001", 7D);
        verify(rankingRedisRepository).setExpire(RankingRedisKeys.dailyCreators(bucketStart.toLocalDate()), Duration.ofDays(2));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.dailyTopics(bucketStart.toLocalDate()), "3001", 7D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.dailyTopics(bucketStart.toLocalDate()), "3002", 7D);
        verify(rankingRedisRepository, times(2)).setExpire(RankingRedisKeys.dailyTopics(bucketStart.toLocalDate()), Duration.ofDays(2));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.weeklyPosts(weekBasedYear, weekNumber), "1001", 7D);
        verify(rankingRedisRepository).setExpire(RankingRedisKeys.weeklyPosts(weekBasedYear, weekNumber), Duration.ofDays(14));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.weeklyCreators(weekBasedYear, weekNumber), "2001", 7D);
        verify(rankingRedisRepository).setExpire(RankingRedisKeys.weeklyCreators(weekBasedYear, weekNumber), Duration.ofDays(14));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), "3001", 7D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), "3002", 7D);
        verify(rankingRedisRepository, times(2)).setExpire(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), Duration.ofDays(14));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.monthlyPosts(2026, 3), "1001", 7D);
        verify(rankingRedisRepository).setExpire(RankingRedisKeys.monthlyPosts(2026, 3), Duration.ofDays(365));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.monthlyCreators(2026, 3), "2001", 7D);
        verify(rankingRedisRepository).setExpire(RankingRedisKeys.monthlyCreators(2026, 3), Duration.ofDays(365));
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.monthlyTopics(2026, 3), "3001", 7D);
        verify(rankingRedisRepository).incrementScore(RankingRedisKeys.monthlyTopics(2026, 3), "3002", 7D);
        verify(rankingRedisRepository, times(2)).setExpire(RankingRedisKeys.monthlyTopics(2026, 3), Duration.ofDays(365));
    }
}
