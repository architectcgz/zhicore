package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.application.port.store.RankingSnapshotCacheStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 基于 Redis 的排行快照缓存实现。
 */
@Component
@RequiredArgsConstructor
public class RedisRankingSnapshotCacheStore implements RankingSnapshotCacheStore {

    private static final Duration DAILY_TTL = Duration.ofDays(2);
    private static final Duration WEEKLY_TTL = Duration.ofDays(14);
    private static final Duration MONTHLY_TTL = Duration.ofDays(365);

    private final RankingRedisRepository rankingRedisRepository;

    @Override
    public void replaceTotalRanking(List<HotScore> postScores, List<HotScore> creatorScores, List<HotScore> topicScores) {
        rankingRedisRepository.replaceRanking(RankingRedisKeys.hotPosts(), postScores, null);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.hotCreators(), creatorScores, null);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.hotTopics(), topicScores, null);
    }

    @Override
    public void replaceDailyRanking(LocalDate date,
                                    List<HotScore> postScores,
                                    List<HotScore> creatorScores,
                                    List<HotScore> topicScores) {
        rankingRedisRepository.replaceRanking(RankingRedisKeys.dailyPosts(date), postScores, DAILY_TTL);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.dailyCreators(date), creatorScores, DAILY_TTL);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.dailyTopics(date), topicScores, DAILY_TTL);
    }

    @Override
    public void replaceWeeklyRanking(int weekBasedYear,
                                     int weekNumber,
                                     List<HotScore> postScores,
                                     List<HotScore> creatorScores,
                                     List<HotScore> topicScores) {
        rankingRedisRepository.replaceRanking(RankingRedisKeys.weeklyPosts(weekBasedYear, weekNumber), postScores, WEEKLY_TTL);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.weeklyCreators(weekBasedYear, weekNumber), creatorScores, WEEKLY_TTL);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.weeklyTopics(weekBasedYear, weekNumber), topicScores, WEEKLY_TTL);
    }

    @Override
    public void replaceMonthlyRanking(int year,
                                      int month,
                                      List<HotScore> postScores,
                                      List<HotScore> creatorScores,
                                      List<HotScore> topicScores) {
        rankingRedisRepository.replaceRanking(RankingRedisKeys.monthlyPosts(year, month), postScores, MONTHLY_TTL);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.monthlyCreators(year, month), creatorScores, MONTHLY_TTL);
        rankingRedisRepository.replaceRanking(RankingRedisKeys.monthlyTopics(year, month), topicScores, MONTHLY_TTL);
    }
}
