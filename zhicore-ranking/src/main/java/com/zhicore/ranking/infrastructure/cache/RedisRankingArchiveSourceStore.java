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
    public List<HotScore> getDailyCreatorRanking(int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.hotCreators(), 0, limit - 1);
    }

    @Override
    public List<HotScore> getDailyTopicRanking(int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.hotTopics(), 0, limit - 1);
    }

    @Override
    public List<HotScore> getWeeklyPostRanking(int weekNumber, int limit) {
        return redisRepository.getWeeklyHotPostsWithScore(weekNumber, limit);
    }

    @Override
    public List<HotScore> getWeeklyCreatorRanking(int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.hotCreators(), 0, limit - 1);
    }

    @Override
    public List<HotScore> getWeeklyTopicRanking(int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.hotTopics(), 0, limit - 1);
    }

    @Override
    public List<HotScore> getMonthlyPostRanking(int year, int month, int limit) {
        return redisRepository.getMonthlyHotPostsWithScore(year, month, limit);
    }

    @Override
    public List<HotScore> getMonthlyCreatorRanking(int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.hotCreators(), 0, limit - 1);
    }

    @Override
    public List<HotScore> getMonthlyTopicRanking(int limit) {
        return redisRepository.getTopRanking(RankingRedisKeys.hotTopics(), 0, limit - 1);
    }
}
