package com.zhicore.user.domain.model;

import com.zhicore.common.util.DateTimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 跨时区签到测试
 * 验证签到业务在不同时区场景下的正确性
 * 
 * 业务时区：Asia/Shanghai (UTC+8)
 * 
 * 关键场景：
 * - UTC 23:00 = 上海 07:00 (次日)
 * - UTC 16:00 = 上海 00:00 (次日)
 * - UTC 15:59 = 上海 23:59 (同日)
 *
 * @author ZhiCore Team
 */
@DisplayName("跨时区签到测试")
class CheckInTimezoneTest {

    @Nested
    @DisplayName("UTC 到业务时区转换")
    class UtcToBusinessZoneConversion {

        @Test
        @DisplayName("UTC 23:00 应该转换为上海次日 07:00")
        void utc2300ShouldBeShanghai0700NextDay() {
            // Given: UTC 2025-01-15 23:00:00
            OffsetDateTime utcTime = OffsetDateTime.of(2025, 1, 15, 23, 0, 0, 0, ZoneOffset.UTC);
            
            // When
            LocalDate businessDate = DateTimeUtils.toBusinessDate(utcTime);
            
            // Then: 上海时间应该是 2025-01-16
            assertEquals(LocalDate.of(2025, 1, 16), businessDate);
        }

        @Test
        @DisplayName("UTC 16:00 应该转换为上海次日 00:00")
        void utc1600ShouldBeShanghai0000NextDay() {
            // Given: UTC 2025-01-15 16:00:00
            OffsetDateTime utcTime = OffsetDateTime.of(2025, 1, 15, 16, 0, 0, 0, ZoneOffset.UTC);
            
            // When
            LocalDate businessDate = DateTimeUtils.toBusinessDate(utcTime);
            
            // Then: 上海时间应该是 2025-01-16
            assertEquals(LocalDate.of(2025, 1, 16), businessDate);
        }

        @Test
        @DisplayName("UTC 15:59 应该转换为上海同日 23:59")
        void utc1559ShouldBeShanghai2359SameDay() {
            // Given: UTC 2025-01-15 15:59:00
            OffsetDateTime utcTime = OffsetDateTime.of(2025, 1, 15, 15, 59, 0, 0, ZoneOffset.UTC);
            
            // When
            LocalDate businessDate = DateTimeUtils.toBusinessDate(utcTime);
            
            // Then: 上海时间应该是 2025-01-15
            assertEquals(LocalDate.of(2025, 1, 15), businessDate);
        }

        @Test
        @DisplayName("UTC 00:00 应该转换为上海同日 08:00")
        void utc0000ShouldBeShanghai0800SameDay() {
            // Given: UTC 2025-01-15 00:00:00
            OffsetDateTime utcTime = OffsetDateTime.of(2025, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
            
            // When
            LocalDate businessDate = DateTimeUtils.toBusinessDate(utcTime);
            
            // Then: 上海时间应该是 2025-01-15
            assertEquals(LocalDate.of(2025, 1, 15), businessDate);
        }
    }

    @Nested
    @DisplayName("跨时区签到防重复")
    class CrossTimezoneCheckInDuplication {

        @Test
        @DisplayName("UTC日期不同但业务日期相同时应该拒绝重复签到")
        void shouldRejectDuplicateCheckInWhenUtcDateDiffersButBusinessDateSame() {
            // Given: 用户在上海时间 2025-01-16 早上签到
            UserCheckInStats stats = UserCheckInStats.create(1L);
            
            // 第一次签到：UTC 2025-01-15 23:00 = 上海 2025-01-16 07:00
            OffsetDateTime firstCheckInUtc = OffsetDateTime.of(2025, 1, 15, 23, 0, 0, 0, ZoneOffset.UTC);
            LocalDate firstBusinessDate = DateTimeUtils.toBusinessDate(firstCheckInUtc);
            stats.recordCheckIn(firstBusinessDate);
            
            // 第二次签到尝试：UTC 2025-01-16 02:00 = 上海 2025-01-16 10:00
            OffsetDateTime secondCheckInUtc = OffsetDateTime.of(2025, 1, 16, 2, 0, 0, 0, ZoneOffset.UTC);
            LocalDate secondBusinessDate = DateTimeUtils.toBusinessDate(secondCheckInUtc);
            
            // When & Then: 两次签到的业务日期相同，应该被识别为同一天
            assertEquals(firstBusinessDate, secondBusinessDate);
            assertTrue(stats.hasCheckedIn(secondBusinessDate));
        }

        @Test
        @DisplayName("UTC日期相同但业务日期不同时应该允许签到")
        void shouldAllowCheckInWhenUtcDateSameButBusinessDateDiffers() {
            // Given: 用户在上海时间 2025-01-15 晚上签到
            UserCheckInStats stats = UserCheckInStats.create(1L);
            
            // 第一次签到：UTC 2025-01-15 15:00 = 上海 2025-01-15 23:00
            OffsetDateTime firstCheckInUtc = OffsetDateTime.of(2025, 1, 15, 15, 0, 0, 0, ZoneOffset.UTC);
            LocalDate firstBusinessDate = DateTimeUtils.toBusinessDate(firstCheckInUtc);
            stats.recordCheckIn(firstBusinessDate);
            
            // 第二次签到：UTC 2025-01-15 17:00 = 上海 2025-01-16 01:00
            OffsetDateTime secondCheckInUtc = OffsetDateTime.of(2025, 1, 15, 17, 0, 0, 0, ZoneOffset.UTC);
            LocalDate secondBusinessDate = DateTimeUtils.toBusinessDate(secondCheckInUtc);
            
            // When & Then: 两次签到的业务日期不同，应该允许签到
            assertNotEquals(firstBusinessDate, secondBusinessDate);
            assertFalse(stats.hasCheckedIn(secondBusinessDate));
            
            // 执行第二次签到
            stats.recordCheckIn(secondBusinessDate);
            assertEquals(2, stats.getTotalDays());
            assertEquals(2, stats.getContinuousDays()); // 连续两天
        }

        @Test
        @DisplayName("跨午夜边界签到应该正确判断日期")
        void shouldCorrectlyDetermineDateAcrossMidnightBoundary() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            
            // UTC 2025-01-15 15:59:59 = 上海 2025-01-15 23:59:59
            OffsetDateTime beforeMidnight = OffsetDateTime.of(2025, 1, 15, 15, 59, 59, 0, ZoneOffset.UTC);
            LocalDate beforeMidnightDate = DateTimeUtils.toBusinessDate(beforeMidnight);
            
            // UTC 2025-01-15 16:00:00 = 上海 2025-01-16 00:00:00
            OffsetDateTime afterMidnight = OffsetDateTime.of(2025, 1, 15, 16, 0, 0, 0, ZoneOffset.UTC);
            LocalDate afterMidnightDate = DateTimeUtils.toBusinessDate(afterMidnight);
            
            // When & Then
            assertEquals(LocalDate.of(2025, 1, 15), beforeMidnightDate);
            assertEquals(LocalDate.of(2025, 1, 16), afterMidnightDate);
            assertNotEquals(beforeMidnightDate, afterMidnightDate);
            
            // 两次签到都应该成功（不同业务日期）
            stats.recordCheckIn(beforeMidnightDate);
            stats.recordCheckIn(afterMidnightDate);
            assertEquals(2, stats.getTotalDays());
        }
    }

    @Nested
    @DisplayName("连续签到跨时区场景")
    class ConsecutiveCheckInCrossTimezone {

        @Test
        @DisplayName("跨时区连续签到应该正确计算连续天数")
        void shouldCorrectlyCalculateConsecutiveDaysAcrossTimezones() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            
            // Day 1: UTC 2025-01-14 20:00 = 上海 2025-01-15 04:00
            OffsetDateTime day1Utc = OffsetDateTime.of(2025, 1, 14, 20, 0, 0, 0, ZoneOffset.UTC);
            LocalDate day1Business = DateTimeUtils.toBusinessDate(day1Utc);
            
            // Day 2: UTC 2025-01-15 23:00 = 上海 2025-01-16 07:00
            OffsetDateTime day2Utc = OffsetDateTime.of(2025, 1, 15, 23, 0, 0, 0, ZoneOffset.UTC);
            LocalDate day2Business = DateTimeUtils.toBusinessDate(day2Utc);
            
            // Day 3: UTC 2025-01-17 01:00 = 上海 2025-01-17 09:00
            OffsetDateTime day3Utc = OffsetDateTime.of(2025, 1, 17, 1, 0, 0, 0, ZoneOffset.UTC);
            LocalDate day3Business = DateTimeUtils.toBusinessDate(day3Utc);
            
            // When
            stats.recordCheckIn(day1Business);
            stats.recordCheckIn(day2Business);
            stats.recordCheckIn(day3Business);
            
            // Then
            assertEquals(LocalDate.of(2025, 1, 15), day1Business);
            assertEquals(LocalDate.of(2025, 1, 16), day2Business);
            assertEquals(LocalDate.of(2025, 1, 17), day3Business);
            assertEquals(3, stats.getTotalDays());
            assertEquals(3, stats.getContinuousDays());
            assertEquals(3, stats.getMaxContinuousDays());
        }

        @Test
        @DisplayName("跨时区断签应该正确重置连续天数")
        void shouldCorrectlyResetConsecutiveDaysOnMissedDayAcrossTimezones() {
            // Given
            UserCheckInStats stats = UserCheckInStats.create(1L);
            
            // Day 1: 上海 2025-01-15
            OffsetDateTime day1Utc = OffsetDateTime.of(2025, 1, 14, 20, 0, 0, 0, ZoneOffset.UTC);
            LocalDate day1Business = DateTimeUtils.toBusinessDate(day1Utc);
            
            // Day 3: 上海 2025-01-17 (跳过了 01-16)
            OffsetDateTime day3Utc = OffsetDateTime.of(2025, 1, 17, 1, 0, 0, 0, ZoneOffset.UTC);
            LocalDate day3Business = DateTimeUtils.toBusinessDate(day3Utc);
            
            // When
            stats.recordCheckIn(day1Business);
            stats.recordCheckIn(day3Business);
            
            // Then
            assertEquals(2, stats.getTotalDays());
            assertEquals(1, stats.getContinuousDays()); // 断签后重置为1
            assertEquals(1, stats.getMaxContinuousDays());
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCases {

        @Test
        @DisplayName("null 时间应该返回 null 日期")
        void shouldReturnNullForNullInput() {
            assertNull(DateTimeUtils.toBusinessDate(null));
            assertNull(DateTimeUtils.toBusinessDateTime(null));
        }

        @Test
        @DisplayName("年末跨年签到应该正确处理")
        void shouldHandleYearEndCrossover() {
            // Given: UTC 2024-12-31 16:00 = 上海 2025-01-01 00:00
            OffsetDateTime newYearUtc = OffsetDateTime.of(2024, 12, 31, 16, 0, 0, 0, ZoneOffset.UTC);
            
            // When
            LocalDate businessDate = DateTimeUtils.toBusinessDate(newYearUtc);
            
            // Then
            assertEquals(LocalDate.of(2025, 1, 1), businessDate);
        }

        @Test
        @DisplayName("月末跨月签到应该正确处理")
        void shouldHandleMonthEndCrossover() {
            // Given: UTC 2025-01-31 16:00 = 上海 2025-02-01 00:00
            OffsetDateTime monthEndUtc = OffsetDateTime.of(2025, 1, 31, 16, 0, 0, 0, ZoneOffset.UTC);
            
            // When
            LocalDate businessDate = DateTimeUtils.toBusinessDate(monthEndUtc);
            
            // Then
            assertEquals(LocalDate.of(2025, 2, 1), businessDate);
        }
    }
}
