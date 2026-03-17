package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.application.port.store.RankingArchiveSourceStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 基于 Redis 的排行榜归档源数据实现。
 */
@Component
@RequiredArgsConstructor
public class RedisRankingArchiveSourceStore implements RankingArchiveSourceStore {

    private final RankingRedisRepository redisRepository;

    @Override
    public List<HotScore> getDailyPostRanking(LocalDate date, int limit) {
        return redisRepository.getDailyHotPostsWithScore(date, limit);
    }

    @Override
    public List<HotScore> getDailyCreatorRanking(LocalDate date, int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.dailyCreators(date), 0, limit - 1);
    }

    @Override
    public List<HotScore> getDailyTopicRanking(LocalDate date, int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.dailyTopics(date), 0, limit - 1);
    }

    @Override
    public List<HotScore> getWeeklyPostRanking(int weekBasedYear, int weekNumber, int limit) {
        return redisRepository.getWeeklyHotPostsWithScore(weekBasedYear, weekNumber, limit);
    }

    @Override
    public List<HotScore> getWeeklyCreatorRanking(int weekBasedYear, int weekNumber, int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.weeklyCreators(weekBasedYear, weekNumber), 0, limit - 1);
    }

    @Override
    public List<HotScore> getWeeklyTopicRanking(int weekBasedYear, int weekNumber, int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), 0, limit - 1);
    }

    @Override
    public List<HotScore> getMonthlyPostRanking(int year, int month, int limit) {
        return redisRepository.getMonthlyHotPostsWithScore(year, month, limit);
    }

    @Override
    public List<HotScore> getMonthlyCreatorRanking(int year, int month, int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.monthlyCreators(year, month), 0, limit - 1);
    }

    @Override
    public List<HotScore> getMonthlyTopicRanking(int year, int month, int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.monthlyTopics(year, month), 0, limit - 1);
    }
}
