package com.zhicore.ranking.domain.service;

import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 热度分数计算器
 *
 * <p>权重从 RankingWeightProperties 实时读取，支持 Nacos 动态刷新（无需重启）</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotScoreCalculator {

    private final RankingWeightProperties weightProperties;

    public double calculatePostHotScore(PostStats stats, LocalDateTime publishedAt) {
        double baseScore = stats.getViewCount() * weightProperties.getView()
                + stats.getLikeCount() * weightProperties.getLike()
                + stats.getCommentCount() * weightProperties.getComment()
                + stats.getFavoriteCount() * weightProperties.getFavorite();

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
        return stats.getFollowersCount() * weightProperties.getFollower()
                + stats.getTotalLikes() * weightProperties.getCreatorLike()
                + stats.getTotalComments() * weightProperties.getCreatorComment()
                + stats.getPostCount() * weightProperties.getPostCount();
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
        return Math.pow(0.5, daysSincePublish / weightProperties.getHalfLifeDays());
    }

    public double getViewDelta() {
        return weightProperties.getView();
    }

    public double getLikeDelta() {
        return weightProperties.getLike();
    }

    public double getCommentDelta() {
        return weightProperties.getComment();
    }

    public double getFavoriteDelta() {
        return weightProperties.getFavorite();
    }
}
