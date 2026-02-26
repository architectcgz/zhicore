package com.zhicore.ranking.domain.service;

import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.model.PostStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 热度分数计算器
 *
 * 文章热度公式：score = (views * viewWeight + likes * likeWeight + comments * commentWeight + favorites * favoriteWeight) * timeDecay
 * 权重从配置读取，支持 Nacos 动态调整
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
public class HotScoreCalculator {

    // 文章热度权重（从配置读取）
    private final double viewWeight;
    private final double likeWeight;
    private final double commentWeight;
    private final double favoriteWeight;

    // 创作者热度权重
    private static final double FOLLOWER_WEIGHT = 2.0;
    private static final double CREATOR_LIKE_WEIGHT = 1.0;
    private static final double CREATOR_COMMENT_WEIGHT = 1.5;
    private static final double POST_COUNT_WEIGHT = 3.0;

    // 时间衰减半衰期（从配置读取）
    private final double halfLifeDays;

    public HotScoreCalculator(
            @Value("${ranking.weight.view:1.0}") double viewWeight,
            @Value("${ranking.weight.like:5.0}") double likeWeight,
            @Value("${ranking.weight.comment:10.0}") double commentWeight,
            @Value("${ranking.weight.favorite:8.0}") double favoriteWeight,
            @Value("${ranking.half-life-days:7}") double halfLifeDays) {
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.commentWeight = commentWeight;
        this.favoriteWeight = favoriteWeight;
        this.halfLifeDays = halfLifeDays;
    }

    /**
     * 计算文章热度分数
     *
     * @param stats       文章统计数据
     * @param publishedAt 发布时间
     * @return 热度分数
     */
    public double calculatePostHotScore(PostStats stats, LocalDateTime publishedAt) {
        double baseScore = stats.getViewCount() * viewWeight
                + stats.getLikeCount() * likeWeight
                + stats.getCommentCount() * commentWeight
                + stats.getFavoriteCount() * favoriteWeight;

        double timeDecay = calculateTimeDecay(publishedAt);
        return baseScore * timeDecay;
    }

    /**
     * 计算创作者热度分数
     *
     * @param stats 创作者统计数据
     * @return 热度分数
     */
    public double calculateCreatorHotScore(CreatorStats stats) {
        return stats.getFollowersCount() * FOLLOWER_WEIGHT
                + stats.getTotalLikes() * CREATOR_LIKE_WEIGHT
                + stats.getTotalComments() * CREATOR_COMMENT_WEIGHT
                + stats.getPostCount() * POST_COUNT_WEIGHT;
    }

    /**
     * 时间衰减函数（指数衰减）
     *
     * @param publishedAt 发布时间
     * @return 衰减因子（0-1之间）
     */
    public double calculateTimeDecay(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 1.0;
        }
        long daysSincePublish = ChronoUnit.DAYS.between(publishedAt, LocalDateTime.now());
        if (daysSincePublish < 0) {
            daysSincePublish = 0;
        }
        return Math.pow(0.5, daysSincePublish / halfLifeDays);
    }

    public double getViewDelta() {
        return viewWeight;
    }

    public double getLikeDelta() {
        return likeWeight;
    }

    public double getCommentDelta() {
        return commentWeight;
    }

    public double getFavoriteDelta() {
        return favoriteWeight;
    }
}
