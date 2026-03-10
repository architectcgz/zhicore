package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.application.port.store.PostRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 基于 Redis 的文章排行榜存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisPostRankingStore implements PostRankingStore {

    private final RankingRedisRepository rankingRepository;

    @Override
    public void updatePostScore(String postId, double score) {
        rankingRepository.updatePostScore(postId, score);
    }

    @Override
    public List<String> getHotPosts(int page, int size) {
        return rankingRepository.getHotPosts(page, size);
    }

    @Override
    public List<HotScore> getHotPostsWithScore(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return rankingRepository.getTopRanking(RankingRedisKeys.hotPosts(), start, end);
    }

    @Override
    public List<String> getDailyHotPosts(LocalDate date, int limit) {
        return rankingRepository.getDailyHotPosts(date, limit);
    }

    @Override
    public List<HotScore> getDailyHotPostsWithScore(LocalDate date, int limit) {
        return rankingRepository.getDailyHotPostsWithScore(date, limit);
    }

    @Override
    public List<String> getWeeklyHotPosts(int weekNumber, int limit) {
        return rankingRepository.getWeeklyHotPosts(weekNumber, limit);
    }

    @Override
    public List<HotScore> getWeeklyHotPostsWithScore(int weekNumber, int limit) {
        return rankingRepository.getWeeklyHotPostsWithScore(weekNumber, limit);
    }

    @Override
    public List<String> getCurrentWeekHotPosts(int limit) {
        return getWeeklyHotPosts(RankingRedisKeys.getCurrentWeekNumber(), limit);
    }

    @Override
    public List<HotScore> getCurrentWeekHotPostsWithScore(int limit) {
        return getWeeklyHotPostsWithScore(RankingRedisKeys.getCurrentWeekNumber(), limit);
    }

    @Override
    public List<String> getMonthlyHotPosts(int year, int month, int limit) {
        return rankingRepository.getMonthlyHotPosts(year, month, limit);
    }

    @Override
    public List<HotScore> getMonthlyHotPostsWithScore(int year, int month, int limit) {
        return rankingRepository.getMonthlyHotPostsWithScore(year, month, limit);
    }

    @Override
    public List<String> getCurrentMonthHotPosts(int limit) {
        LocalDate now = LocalDate.now();
        return getMonthlyHotPosts(now.getYear(), now.getMonthValue(), limit);
    }

    @Override
    public List<HotScore> getCurrentMonthHotPostsWithScore(int limit) {
        LocalDate now = LocalDate.now();
        return getMonthlyHotPostsWithScore(now.getYear(), now.getMonthValue(), limit);
    }

    @Override
    public Long getPostRank(String postId) {
        Long rank = rankingRepository.getRank(RankingRedisKeys.hotPosts(), postId);
        return rank != null ? rank + 1 : null;
    }

    @Override
    public Double getPostScore(String postId) {
        return rankingRepository.getScore(RankingRedisKeys.hotPosts(), postId);
    }

    @Override
    public void removePost(String postId) {
        rankingRepository.removeMember(RankingRedisKeys.hotPosts(), postId);
    }
}
