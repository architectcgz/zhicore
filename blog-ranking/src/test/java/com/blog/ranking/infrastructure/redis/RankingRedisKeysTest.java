package com.blog.ranking.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排行榜 Redis Key 测试
 *
 * @author Blog Team
 */
@DisplayName("RankingRedisKeys Tests")
class RankingRedisKeysTest {

    @Test
    @DisplayName("生成文章日榜Key")
    void dailyPostsKey() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);

        // When
        String key = RankingRedisKeys.dailyPosts(date);

        // Then
        assertEquals("ranking:posts:daily:2024-01-15", key);
    }

    @Test
    @DisplayName("生成今日文章日榜Key")
    void todayPostsKey() {
        // When
        String key = RankingRedisKeys.todayPosts();

        // Then
        assertTrue(key.startsWith("ranking:posts:daily:"));
        assertTrue(key.contains(LocalDate.now().toString()));
    }

    @Test
    @DisplayName("生成文章周榜Key")
    void weeklyPostsKey() {
        // Given
        int weekNumber = 5;

        // When
        String key = RankingRedisKeys.weeklyPosts(weekNumber);

        // Then
        assertEquals("ranking:posts:weekly:5", key);
    }

    @Test
    @DisplayName("生成本周文章周榜Key")
    void currentWeekPostsKey() {
        // When
        String key = RankingRedisKeys.currentWeekPosts();

        // Then
        assertTrue(key.startsWith("ranking:posts:weekly:"));
    }

    @Test
    @DisplayName("生成创作者日榜Key")
    void dailyCreatorsKey() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);

        // When
        String key = RankingRedisKeys.dailyCreators(date);

        // Then
        assertEquals("ranking:creators:daily:2024-01-15", key);
    }

    @Test
    @DisplayName("生成话题日榜Key")
    void dailyTopicsKey() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);

        // When
        String key = RankingRedisKeys.dailyTopics(date);

        // Then
        assertEquals("ranking:topics:daily:2024-01-15", key);
    }

    @Test
    @DisplayName("获取当前周数")
    void getCurrentWeekNumber() {
        // When
        int weekNumber = RankingRedisKeys.getCurrentWeekNumber();

        // Then
        assertTrue(weekNumber >= 1 && weekNumber <= 53, "Week number should be between 1 and 53");
    }

    @Test
    @DisplayName("常量Key值正确")
    void constantKeys() {
        assertEquals("ranking:posts:hot", RankingRedisKeys.hotPosts());
        assertEquals("ranking:creators:hot", RankingRedisKeys.hotCreators());
        assertEquals("ranking:topics:hot", RankingRedisKeys.hotTopics());
    }

    @Test
    @DisplayName("生成文章月榜Key - 单数月份格式正确（两位数）")
    void monthlyPostsKey_SingleDigitMonth() {
        // Given
        int year = 2024;
        int month = 1;

        // When
        String key = RankingRedisKeys.monthlyPosts(year, month);

        // Then
        assertEquals("ranking:posts:monthly:2024:01", key, "单数月份应该格式化为两位数");
    }

    @Test
    @DisplayName("生成文章月榜Key - 双数月份格式正确")
    void monthlyPostsKey_DoubleDigitMonth() {
        // Given
        int year = 2024;
        int month = 12;

        // When
        String key = RankingRedisKeys.monthlyPosts(year, month);

        // Then
        assertEquals("ranking:posts:monthly:2024:12", key);
    }

    @Test
    @DisplayName("生成文章月榜Key - 所有月份格式正确")
    void monthlyPostsKey_AllMonths() {
        // Given
        int year = 2024;
        String[] expectedMonths = {
            "01", "02", "03", "04", "05", "06",
            "07", "08", "09", "10", "11", "12"
        };

        // When & Then
        for (int month = 1; month <= 12; month++) {
            String key = RankingRedisKeys.monthlyPosts(year, month);
            String expectedKey = "ranking:posts:monthly:2024:" + expectedMonths[month - 1];
            assertEquals(expectedKey, key, 
                String.format("月份 %d 应该格式化为 %s", month, expectedMonths[month - 1]));
        }
    }

    @Test
    @DisplayName("生成本月文章月榜Key")
    void currentMonthPostsKey() {
        // Given
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String expectedMonth = String.format("%02d", month);

        // When
        String key = RankingRedisKeys.currentMonthPosts();

        // Then
        String expectedKey = "ranking:posts:monthly:" + year + ":" + expectedMonth;
        assertEquals(expectedKey, key);
        assertTrue(key.startsWith("ranking:posts:monthly:"));
    }
}
