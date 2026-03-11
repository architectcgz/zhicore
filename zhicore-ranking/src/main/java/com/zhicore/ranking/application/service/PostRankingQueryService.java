package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.PostRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 文章排行榜查询服务。
 */
@Service
@RequiredArgsConstructor
public class PostRankingQueryService {

    private final PostRankingStore postRankingStore;

    public List<String> getHotPosts(int page, int size) {
        return postRankingStore.getHotPosts(page, size);
    }

    public List<HotScore> getHotPostsWithScore(int page, int size) {
        return postRankingStore.getHotPostsWithScore(page, size);
    }

    public List<String> getDailyHotPosts(LocalDate date, int limit) {
        return postRankingStore.getDailyHotPosts(date, limit);
    }

    public List<String> getTodayHotPosts(int limit) {
        return getDailyHotPosts(LocalDate.now(), limit);
    }

    public List<String> getWeeklyHotPosts(int weekNumber, int limit) {
        return postRankingStore.getWeeklyHotPosts(weekNumber, limit);
    }

    public List<String> getCurrentWeekHotPosts(int limit) {
        return postRankingStore.getCurrentWeekHotPosts(limit);
    }

    public List<HotScore> getDailyHotPostsWithScore(LocalDate date, int limit) {
        return postRankingStore.getDailyHotPostsWithScore(date, limit);
    }

    public List<HotScore> getTodayHotPostsWithScore(int limit) {
        return getDailyHotPostsWithScore(LocalDate.now(), limit);
    }

    public List<HotScore> getWeeklyHotPostsWithScore(int weekNumber, int limit) {
        return postRankingStore.getWeeklyHotPostsWithScore(weekNumber, limit);
    }

    public List<HotScore> getCurrentWeekHotPostsWithScore(int limit) {
        return postRankingStore.getCurrentWeekHotPostsWithScore(limit);
    }

    public List<String> getMonthlyHotPosts(int year, int month, int limit) {
        return postRankingStore.getMonthlyHotPosts(year, month, limit);
    }

    public List<String> getCurrentMonthHotPosts(int limit) {
        return postRankingStore.getCurrentMonthHotPosts(limit);
    }

    public List<HotScore> getMonthlyHotPostsWithScore(int year, int month, int limit) {
        return postRankingStore.getMonthlyHotPostsWithScore(year, month, limit);
    }

    public List<HotScore> getCurrentMonthHotPostsWithScore(int limit) {
        return postRankingStore.getCurrentMonthHotPostsWithScore(limit);
    }

    public Long getPostRank(String postId) {
        return postRankingStore.getPostRank(postId);
    }

    public Double getPostScore(String postId) {
        return postRankingStore.getPostScore(postId);
    }
}
