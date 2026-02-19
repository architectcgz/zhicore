package com.blog.ranking.domain.service;

import com.blog.ranking.domain.model.CreatorStats;
import com.blog.ranking.domain.model.PostStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 热度分数计算器测试
 *
 * @author Blog Team
 */
@DisplayName("HotScoreCalculator Tests")
class HotScoreCalculatorTest {

    private HotScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new HotScoreCalculator();
    }

    // ==================== 文章热度计算测试 ====================

    @Test
    @DisplayName("计算文章热度分数 - 基础场景")
    void calculatePostHotScore_basicScenario() {
        // Given
        PostStats stats = PostStats.builder()
                .viewCount(100)
                .likeCount(10)
                .commentCount(5)
                .favoriteCount(3)
                .build();
        LocalDateTime publishedAt = LocalDateTime.now();

        // When
        double score = calculator.calculatePostHotScore(stats, publishedAt);

        // Then
        // 基础分数 = 100*1 + 10*5 + 5*10 + 3*8 = 100 + 50 + 50 + 24 = 224
        // 时间衰减因子 ≈ 1.0（刚发布）
        assertTrue(score > 220 && score <= 224, "Score should be around 224 for just published post");
    }

    @Test
    @DisplayName("计算文章热度分数 - 7天后衰减为一半")
    void calculatePostHotScore_halfDecayAfter7Days() {
        // Given
        PostStats stats = PostStats.builder()
                .viewCount(100)
                .likeCount(10)
                .commentCount(5)
                .favoriteCount(3)
                .build();
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(7);

        // When
        double score = calculator.calculatePostHotScore(stats, publishedAt);

        // Then
        // 基础分数 = 224
        // 7天后时间衰减因子 = 0.5
        // 预期分数 ≈ 224 * 0.5 = 112
        assertTrue(score > 110 && score < 115, "Score should be around 112 after 7 days");
    }

    @Test
    @DisplayName("计算文章热度分数 - 14天后衰减为四分之一")
    void calculatePostHotScore_quarterDecayAfter14Days() {
        // Given
        PostStats stats = PostStats.builder()
                .viewCount(100)
                .likeCount(10)
                .commentCount(5)
                .favoriteCount(3)
                .build();
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(14);

        // When
        double score = calculator.calculatePostHotScore(stats, publishedAt);

        // Then
        // 基础分数 = 224
        // 14天后时间衰减因子 = 0.25
        // 预期分数 ≈ 224 * 0.25 = 56
        assertTrue(score > 54 && score < 58, "Score should be around 56 after 14 days");
    }

    @Test
    @DisplayName("计算文章热度分数 - 空统计数据")
    void calculatePostHotScore_emptyStats() {
        // Given
        PostStats stats = PostStats.empty();
        LocalDateTime publishedAt = LocalDateTime.now();

        // When
        double score = calculator.calculatePostHotScore(stats, publishedAt);

        // Then
        assertEquals(0.0, score, "Score should be 0 for empty stats");
    }

    @Test
    @DisplayName("计算文章热度分数 - null发布时间不应用衰减")
    void calculatePostHotScore_nullPublishedAt() {
        // Given
        PostStats stats = PostStats.builder()
                .viewCount(100)
                .likeCount(10)
                .commentCount(5)
                .favoriteCount(3)
                .build();

        // When
        double score = calculator.calculatePostHotScore(stats, null);

        // Then
        // 基础分数 = 224，时间衰减因子 = 1.0
        assertEquals(224.0, score, "Score should be 224 without time decay");
    }

    // ==================== 创作者热度计算测试 ====================

    @Test
    @DisplayName("计算创作者热度分数 - 基础场景")
    void calculateCreatorHotScore_basicScenario() {
        // Given
        CreatorStats stats = CreatorStats.builder()
                .followersCount(100)
                .totalLikes(500)
                .totalComments(200)
                .postCount(50)
                .build();

        // When
        double score = calculator.calculateCreatorHotScore(stats);

        // Then
        // 分数 = 100*2 + 500*1 + 200*1.5 + 50*3 = 200 + 500 + 300 + 150 = 1150
        assertEquals(1150.0, score, "Creator score should be 1150");
    }

    @Test
    @DisplayName("计算创作者热度分数 - 空统计数据")
    void calculateCreatorHotScore_emptyStats() {
        // Given
        CreatorStats stats = CreatorStats.empty();

        // When
        double score = calculator.calculateCreatorHotScore(stats);

        // Then
        assertEquals(0.0, score, "Score should be 0 for empty stats");
    }

    // ==================== 时间衰减测试 ====================

    @Test
    @DisplayName("时间衰减 - 刚发布")
    void calculateTimeDecay_justPublished() {
        // Given
        LocalDateTime publishedAt = LocalDateTime.now();

        // When
        double decay = calculator.calculateTimeDecay(publishedAt);

        // Then
        assertTrue(decay > 0.99 && decay <= 1.0, "Decay should be close to 1.0 for just published");
    }

    @Test
    @DisplayName("时间衰减 - 7天后")
    void calculateTimeDecay_after7Days() {
        // Given
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(7);

        // When
        double decay = calculator.calculateTimeDecay(publishedAt);

        // Then
        assertTrue(decay > 0.49 && decay < 0.51, "Decay should be around 0.5 after 7 days");
    }

    @Test
    @DisplayName("时间衰减 - null时间返回1.0")
    void calculateTimeDecay_nullTime() {
        // When
        double decay = calculator.calculateTimeDecay(null);

        // Then
        assertEquals(1.0, decay, "Decay should be 1.0 for null time");
    }

    @Test
    @DisplayName("时间衰减 - 未来时间返回1.0")
    void calculateTimeDecay_futureTime() {
        // Given
        LocalDateTime publishedAt = LocalDateTime.now().plusDays(1);

        // When
        double decay = calculator.calculateTimeDecay(publishedAt);

        // Then
        assertEquals(1.0, decay, "Decay should be 1.0 for future time");
    }

    // ==================== 权重获取测试 ====================

    @Test
    @DisplayName("获取浏览增量权重")
    void getViewDelta() {
        assertEquals(1.0, calculator.getViewDelta());
    }

    @Test
    @DisplayName("获取点赞增量权重")
    void getLikeDelta() {
        assertEquals(5.0, calculator.getLikeDelta());
    }

    @Test
    @DisplayName("获取评论增量权重")
    void getCommentDelta() {
        assertEquals(10.0, calculator.getCommentDelta());
    }

    @Test
    @DisplayName("获取收藏增量权重")
    void getFavoriteDelta() {
        assertEquals(8.0, calculator.getFavoriteDelta());
    }

    // ==================== 排行榜有序性测试 ====================

    @Test
    @DisplayName("热度分数有序性 - 更多互动应该有更高分数")
    void hotScore_ordering_moreInteractionHigherScore() {
        // Given
        PostStats lowStats = PostStats.builder()
                .viewCount(10)
                .likeCount(1)
                .commentCount(0)
                .favoriteCount(0)
                .build();

        PostStats highStats = PostStats.builder()
                .viewCount(1000)
                .likeCount(100)
                .commentCount(50)
                .favoriteCount(30)
                .build();

        LocalDateTime publishedAt = LocalDateTime.now();

        // When
        double lowScore = calculator.calculatePostHotScore(lowStats, publishedAt);
        double highScore = calculator.calculatePostHotScore(highStats, publishedAt);

        // Then
        assertTrue(highScore > lowScore, "Higher interaction should result in higher score");
    }

    @Test
    @DisplayName("热度分数有序性 - 新文章应该比旧文章分数高（相同互动）")
    void hotScore_ordering_newerPostHigherScore() {
        // Given
        PostStats stats = PostStats.builder()
                .viewCount(100)
                .likeCount(10)
                .commentCount(5)
                .favoriteCount(3)
                .build();

        LocalDateTime newPost = LocalDateTime.now();
        LocalDateTime oldPost = LocalDateTime.now().minusDays(30);

        // When
        double newScore = calculator.calculatePostHotScore(stats, newPost);
        double oldScore = calculator.calculatePostHotScore(stats, oldPost);

        // Then
        assertTrue(newScore > oldScore, "Newer post should have higher score");
    }
}
