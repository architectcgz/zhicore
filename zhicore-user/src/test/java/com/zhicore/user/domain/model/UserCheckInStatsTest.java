package com.zhicore.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserCheckInStats 值对象单元测试
 *
 * @author ZhiCore Team
 */
@DisplayName("UserCheckInStats 值对象测试")
class UserCheckInStatsTest {

    @Nested
    @DisplayName("签到记录")
    class RecordCheckIn {

        @Test
        @DisplayName("首次签到应该正确初始化统计")
        void shouldInitializeStatsOnFirstCheckIn() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            LocalDate today = LocalDate.now();

            // When
            stats.recordCheckIn(today);

            // Then
            assertEquals(1, stats.getTotalDays());
            assertEquals(1, stats.getContinuousDays());
            assertEquals(1, stats.getMaxContinuousDays());
            assertEquals(today, stats.getLastCheckInDate());
        }

        @Test
        @DisplayName("连续签到应该增加连续天数")
        void shouldIncrementContinuousDaysOnConsecutiveCheckIn() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDate today = LocalDate.now();

            // When
            stats.recordCheckIn(yesterday);
            stats.recordCheckIn(today);

            // Then
            assertEquals(2, stats.getTotalDays());
            assertEquals(2, stats.getContinuousDays());
            assertEquals(2, stats.getMaxContinuousDays());
        }

        @Test
        @DisplayName("断签后应该重置连续天数")
        void shouldResetContinuousDaysOnMissedDay() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            LocalDate twoDaysAgo = LocalDate.now().minusDays(2);
            LocalDate today = LocalDate.now();

            // When
            stats.recordCheckIn(twoDaysAgo);
            stats.recordCheckIn(today);

            // Then
            assertEquals(2, stats.getTotalDays());
            assertEquals(1, stats.getContinuousDays());
            assertEquals(1, stats.getMaxContinuousDays());
        }

        @Test
        @DisplayName("断签后最大连续天数应该保持")
        void shouldKeepMaxContinuousDaysAfterMissedDay() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            LocalDate day1 = LocalDate.now().minusDays(5);
            LocalDate day2 = LocalDate.now().minusDays(4);
            LocalDate day3 = LocalDate.now().minusDays(3);
            LocalDate today = LocalDate.now();

            // When - 连续签到3天，然后断签
            stats.recordCheckIn(day1);
            stats.recordCheckIn(day2);
            stats.recordCheckIn(day3);
            stats.recordCheckIn(today);

            // Then
            assertEquals(4, stats.getTotalDays());
            assertEquals(1, stats.getContinuousDays());
            assertEquals(3, stats.getMaxContinuousDays());
        }
    }

    @Nested
    @DisplayName("签到状态检查")
    class CheckInStatus {

        @Test
        @DisplayName("今日已签到应该返回true")
        void shouldReturnTrueWhenCheckedInToday() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            stats.recordCheckIn(LocalDate.now());

            // When & Then
            assertTrue(stats.hasCheckedInToday());
        }

        @Test
        @DisplayName("今日未签到应该返回false")
        void shouldReturnFalseWhenNotCheckedInToday() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            stats.recordCheckIn(LocalDate.now().minusDays(1));

            // When & Then
            assertFalse(stats.hasCheckedInToday());
        }

        @Test
        @DisplayName("从未签到应该返回false")
        void shouldReturnFalseWhenNeverCheckedIn() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);

            // When & Then
            assertFalse(stats.hasCheckedInToday());
        }
    }
}
