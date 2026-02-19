package com.blog.ranking.application.service;

import com.blog.ranking.application.dto.RankingPostVO;
import com.blog.ranking.domain.model.HotScore;
import com.blog.ranking.domain.model.PostStats;
import com.blog.ranking.domain.service.HotScoreCalculator;
import com.blog.ranking.infrastructure.redis.RankingRedisKeys;
import com.blog.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章排行榜服务
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRankingService {

    private final RankingRedisRepository rankingRepository;
    private final HotScoreCalculator scoreCalculator;

    /**
     * 更新文章热度分数
     *
     * @param postId 文章ID
     * @param stats 文章统计数据
     * @param publishedAt 发布时间
     */
    public void updatePostScore(String postId, PostStats stats, LocalDateTime publishedAt) {
        double score = scoreCalculator.calculatePostHotScore(stats, publishedAt);
        rankingRepository.updatePostScore(postId, score);
        log.debug("Updated post score: postId={}, score={}", postId, score);
    }

    /**
     * 获取热门文章排行（总榜）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 文章ID列表
     */
    public List<String> getHotPosts(int page, int size) {
        return rankingRepository.getHotPosts(page, size);
    }

    /**
     * 获取热门文章排行带分数（总榜）
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    public List<HotScore> getHotPostsWithScore(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return rankingRepository.getTopRanking(RankingRedisKeys.hotPosts(), start, end);
    }

    /**
     * 获取热门文章排行（日榜）
     *
     * @param date 日期
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getDailyHotPosts(LocalDate date, int limit) {
        return rankingRepository.getDailyHotPosts(date, limit);
    }

    /**
     * 获取今日热门文章
     *
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getTodayHotPosts(int limit) {
        return getDailyHotPosts(LocalDate.now(), limit);
    }

    /**
     * 获取热门文章排行（周榜）
     *
     * @param weekNumber 周数
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getWeeklyHotPosts(int weekNumber, int limit) {
        return rankingRepository.getWeeklyHotPosts(weekNumber, limit);
    }

    /**
     * 获取本周热门文章
     *
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getCurrentWeekHotPosts(int limit) {
        return getWeeklyHotPosts(RankingRedisKeys.getCurrentWeekNumber(), limit);
    }

    /**
     * 获取热门文章排行带分数（日榜）
     *
     * @param date 日期
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getDailyHotPostsWithScore(LocalDate date, int limit) {
        return rankingRepository.getDailyHotPostsWithScore(date, limit);
    }

    /**
     * 获取今日热门文章带分数
     *
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getTodayHotPostsWithScore(int limit) {
        return getDailyHotPostsWithScore(LocalDate.now(), limit);
    }

    /**
     * 获取热门文章排行带分数（周榜）
     *
     * @param weekNumber 周数
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getWeeklyHotPostsWithScore(int weekNumber, int limit) {
        return rankingRepository.getWeeklyHotPostsWithScore(weekNumber, limit);
    }

    /**
     * 获取本周热门文章带分数
     *
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getCurrentWeekHotPostsWithScore(int limit) {
        return getWeeklyHotPostsWithScore(RankingRedisKeys.getCurrentWeekNumber(), limit);
    }

    /**
     * 获取热门文章排行（月榜）
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getMonthlyHotPosts(int year, int month, int limit) {
        return rankingRepository.getMonthlyHotPosts(year, month, limit);
    }

    /**
     * 获取本月热门文章
     *
     * @param limit 数量限制
     * @return 文章ID列表
     */
    public List<String> getCurrentMonthHotPosts(int limit) {
        LocalDate now = LocalDate.now();
        return getMonthlyHotPosts(now.getYear(), now.getMonthValue(), limit);
    }

    /**
     * 获取热门文章排行带分数（月榜）
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getMonthlyHotPostsWithScore(int year, int month, int limit) {
        return rankingRepository.getMonthlyHotPostsWithScore(year, month, limit);
    }

    /**
     * 获取本月热门文章带分数
     *
     * @param limit 数量限制
     * @return 热度分数列表
     */
    public List<HotScore> getCurrentMonthHotPostsWithScore(int limit) {
        LocalDate now = LocalDate.now();
        return getMonthlyHotPostsWithScore(now.getYear(), now.getMonthValue(), limit);
    }

    /**
     * 获取文章排名
     *
     * @param postId 文章ID
     * @return 排名（从1开始），如果不在排行榜中返回null
     */
    public Long getPostRank(String postId) {
        Long rank = rankingRepository.getRank(RankingRedisKeys.hotPosts(), postId);
        return rank != null ? rank + 1 : null;
    }

    /**
     * 获取文章热度分数
     *
     * @param postId 文章ID
     * @return 热度分数
     */
    public Double getPostScore(String postId) {
        return rankingRepository.getScore(RankingRedisKeys.hotPosts(), postId);
    }

    /**
     * 从排行榜中移除文章
     *
     * @param postId 文章ID
     */
    public void removePost(String postId) {
        rankingRepository.removeMember(RankingRedisKeys.hotPosts(), postId);
        log.info("Removed post from ranking: postId={}", postId);
    }
}
