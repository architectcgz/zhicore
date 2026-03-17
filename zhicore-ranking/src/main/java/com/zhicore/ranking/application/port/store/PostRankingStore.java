package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.domain.model.HotScore;

import java.time.LocalDate;
import java.util.List;

/**
 * 文章排行榜存储端口。
 */
public interface PostRankingStore {

    void updatePostScore(String postId, double score);

    List<String> getHotPosts(int page, int size);

    List<HotScore> getHotPostsWithScore(int page, int size);

    List<String> getDailyHotPosts(LocalDate date, int limit);

    List<HotScore> getDailyHotPostsWithScore(LocalDate date, int limit);

    List<String> getWeeklyHotPosts(int weekBasedYear, int weekNumber, int limit);

    List<HotScore> getWeeklyHotPostsWithScore(int weekBasedYear, int weekNumber, int limit);

    List<String> getCurrentWeekHotPosts(int limit);

    List<HotScore> getCurrentWeekHotPostsWithScore(int limit);

    List<String> getMonthlyHotPosts(int year, int month, int limit);

    List<HotScore> getMonthlyHotPostsWithScore(int year, int month, int limit);

    List<String> getCurrentMonthHotPosts(int limit);

    List<HotScore> getCurrentMonthHotPostsWithScore(int limit);

    Long getPostRank(String postId);

    Double getPostScore(String postId);

    void removePost(String postId);
}
