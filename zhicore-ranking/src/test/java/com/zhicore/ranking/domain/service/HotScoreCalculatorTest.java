package com.zhicore.ranking.domain.service;

import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 热度分数计算器测试
 *
 * @author ZhiCore Team
 */
@DisplayName("HotScoreCalculator Tests")
class HotScoreCalculatorTest {

    private HotScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        RankingWeightProperties props = new RankingWeightProperties();
        props.setView(1.0);
        props.setLike(5.0);
        props.setComment(10.0);
        props.setFavorite(8.0);
        props.setHalfLifeDays(7.0);
        calculator = new HotScoreCalculator(props);
    }

    // ==================== 文章热度计算测试 ====================

    @Test
    @DisplayName("计算文章热度分数 - 基础场景")
    void calculatePostHotScore_basicScenario() {
        PostStats stats = PostStats.builder()
                .viewCount(100).likeCount(10).commentCount(5).favoriteCount(3).build();
        LocalDateTime publishedAt = LocalDateTime.now();

        double score = calculator.calculatePostHotScore(stats, publishedAt);
        // 基础分数 = 100*1 + 10*5 + 5*10 + 3*8 = 224，衰减因子 ≈ 1.0
        assertTrue(score > 220 && score <= 224);
    }

    @Test
    @DisplayName("计算文章热度分数 - 7天后衰减为一半")
    void calculatePostHotScore_halfDecayAfter7Days() {
        PostStats stats = PostStats.builder()
                .viewCount(100).likeCount(10).commentCount(5).favoriteCount(3).build();
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(7);

        double score = calculator.calculatePostHotScore(stats, publishedAt);
        assertTrue(score > 110 && score < 115);
    }

    @Test
    @DisplayName("计算文章热度分数 - 14天后衰减为四分之一")
    void calculatePostHotScore_quarterDecayAfter14Days() {
        PostStats stats = PostStats.builder()
                .viewCount(100).likeCount(10).commentCount(5).favoriteCount(3).build();
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(14);

        double score = calculator.calculatePostHotScore(stats, publishedAt);
        assertTrue(score > 54 && score < 58);
    }

    @Test
    @DisplayName("计算文章热度分数 - 空统计数据")
    void calculatePostHotScore_emptyStats() {
        PostStats stats = PostStats.empty();
        double score = calculator.calculatePostHotScore(stats, LocalDateTime.now());
        assertEquals(0.0, score);
    }

    @Test
    @DisplayName("计算文章热度分数 - null发布时间不应用衰减")
    void calculatePostHotScore_nullPublishedAt() {
        PostStats stats = PostStats.builder()
                .viewCount(100).likeCount(10).commentCount(5).favoriteCount(3).build();
        double score = calculator.calculatePostHotScore(stats, null);
        assertEquals(224.0, score);
    }

    // ==================== 创作者热度计算测试 ====================

    @Test
    @DisplayName("计算创作者热度分数 - 基础场景")
    void calculateCreatorHotScore_basicScenario() {
        CreatorStats stats = CreatorStats.builder()
                .followersCount(100).totalLikes(500).totalComments(200).postCount(50).build();
        double score = calculator.calculateCreatorHotScore(stats);
        assertEquals(1150.0, score);
    }

    @Test
    @DisplayName("计算创作者热度分数 - 空统计数据")
    void calculateCreatorHotScore_emptyStats() {
        double score = calculator.calculateCreatorHotScore(CreatorStats.empty());
        assertEquals(0.0, score);
    }

    // ==================== 时间衰减测试 ====================

    @Test
    @DisplayName("时间衰减 - 刚发布")
    void calculateTimeDecay_justPublished() {
        double decay = calculator.calculateTimeDecay(LocalDateTime.now());
        assertTrue(decay > 0.99 && decay <= 1.0);
    }

    @Test
    @DisplayName("时间衰减 - 7天后")
    void calculateTimeDecay_after7Days() {
        double decay = calculator.calculateTimeDecay(LocalDateTime.now().minusDays(7));
        assertTrue(decay > 0.49 && decay < 0.51);
    }

    @Test
    @DisplayName("时间衰减 - null时间返回1.0")
    void calculateTimeDecay_nullTime() {
        assertEquals(1.0, calculator.calculateTimeDecay(null));
    }

    @Test
    @DisplayName("时间衰减 - 未来时间返回1.0")
    void calculateTimeDecay_futureTime() {
        assertEquals(1.0, calculator.calculateTimeDecay(LocalDateTime.now().plusDays(1)));
    }

    // ==================== 权重获取测试 ====================

    @Test
    @DisplayName("获取各权重值")
    void getDeltaWeights() {
        assertEquals(1.0, calculator.getViewDelta());
        assertEquals(5.0, calculator.getLikeDelta());
        assertEquals(10.0, calculator.getCommentDelta());
        assertEquals(8.0, calculator.getFavoriteDelta());
    }

    // ==================== 自定义权重测试 ====================

    @Test
    @DisplayName("自定义权重 - 权重从配置读取")
    void customWeights() {
        RankingWeightProperties customProps = new RankingWeightProperties();
        customProps.setView(2.0);
        customProps.setLike(10.0);
        customProps.setComment(20.0);
        customProps.setFavorite(16.0);
        customProps.setHalfLifeDays(7.0);
        HotScoreCalculator custom = new HotScoreCalculator(customProps);
        PostStats stats = PostStats.builder()
                .viewCount(100).likeCount(10).commentCount(5).favoriteCount(3).build();

        double score = custom.calculatePostHotScore(stats, null);
        // 100*2 + 10*10 + 5*20 + 3*16 = 200 + 100 + 100 + 48 = 448
        assertEquals(448.0, score);
    }

    // ==================== 排行榜有序性测试 ====================

    @Test
    @DisplayName("热度分数有序性 - 更多互动应该有更高分数")
    void hotScore_ordering_moreInteractionHigherScore() {
        PostStats lowStats = PostStats.builder()
                .viewCount(10).likeCount(1).commentCount(0).favoriteCount(0).build();
        PostStats highStats = PostStats.builder()
                .viewCount(1000).likeCount(100).commentCount(50).favoriteCount(30).build();
        LocalDateTime publishedAt = LocalDateTime.now();

        double lowScore = calculator.calculatePostHotScore(lowStats, publishedAt);
        double highScore = calculator.calculatePostHotScore(highStats, publishedAt);
        assertTrue(highScore > lowScore);
    }

    @Test
    @DisplayName("热度分数有序性 - 新文章应该比旧文章分数高")
    void hotScore_ordering_newerPostHigherScore() {
        PostStats stats = PostStats.builder()
                .viewCount(100).likeCount(10).commentCount(5).favoriteCount(3).build();

        double newScore = calculator.calculatePostHotScore(stats, LocalDateTime.now());
        double oldScore = calculator.calculatePostHotScore(stats, LocalDateTime.now().minusDays(30));
        assertTrue(newScore > oldScore);
    }
}
