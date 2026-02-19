package com.blog.ranking.domain.service;

import com.blog.ranking.domain.model.CreatorStats;
import com.blog.ranking.domain.model.PostStats;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 热度分数计算器
 * 
 * 文章热度公式：score = (views * 1 + likes * 5 + comments * 10 + favorites * 8) * timeDecay
 * 创作者热度公式：score = followers * 2 + totalLikes * 1 + totalComments * 1.5 + postCount * 3
 *
 * @author Blog Team
 */
@Service
public class HotScoreCalculator {

    // 热度权重配置
    private static final double VIEW_WEIGHT = 1.0;
    private static final double LIKE_WEIGHT = 5.0;
    private static final double COMMENT_WEIGHT = 10.0;
    private static final double FAVORITE_WEIGHT = 8.0;

    // 创作者热度权重
    private static final double FOLLOWER_WEIGHT = 2.0;
    private static final double CREATOR_LIKE_WEIGHT = 1.0;
    private static final double CREATOR_COMMENT_WEIGHT = 1.5;
    private static final double POST_COUNT_WEIGHT = 3.0;

    // 时间衰减因子（半衰期：7天）
    private static final double HALF_LIFE_DAYS = 7.0;

    /**
     * 计算文章热度分数
     * 公式：score = (views * 1 + likes * 5 + comments * 10 + favorites * 8) * timeDecay
     *
     * @param stats 文章统计数据
     * @param publishedAt 发布时间
     * @return 热度分数
     */
    public double calculatePostHotScore(PostStats stats, LocalDateTime publishedAt) {
        double baseScore = stats.getViewCount() * VIEW_WEIGHT
                + stats.getLikeCount() * LIKE_WEIGHT
                + stats.getCommentCount() * COMMENT_WEIGHT
                + stats.getFavoriteCount() * FAVORITE_WEIGHT;

        double timeDecay = calculateTimeDecay(publishedAt);
        return baseScore * timeDecay;
    }

    /**
     * 计算创作者热度分数
     * 公式：score = followers * 2 + totalLikes * 1 + totalComments * 1.5 + postCount * 3
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
     * 使用半衰期模型：每过7天，热度衰减为原来的一半
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
        return Math.pow(0.5, daysSincePublish / HALF_LIFE_DAYS);
    }

    /**
     * 获取浏览增量权重
     */
    public double getViewDelta() {
        return VIEW_WEIGHT;
    }

    /**
     * 获取点赞增量权重
     */
    public double getLikeDelta() {
        return LIKE_WEIGHT;
    }

    /**
     * 获取评论增量权重
     */
    public double getCommentDelta() {
        return COMMENT_WEIGHT;
    }

    /**
     * 获取收藏增量权重
     */
    public double getFavoriteDelta() {
        return FAVORITE_WEIGHT;
    }
}
