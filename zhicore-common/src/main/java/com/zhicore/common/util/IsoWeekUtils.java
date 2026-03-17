package com.zhicore.common.util;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

/**
 * ISO 周历工具类。
 *
 * <p>统一封装 week-based year / week number 的计算与校验，避免各服务各自实现导致边界不一致。</p>
 */
public final class IsoWeekUtils {

    private IsoWeekUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 获取日期所属的 ISO week-based year。
     */
    public static int getWeekBasedYear(LocalDate date) {
        return date.get(WeekFields.ISO.weekBasedYear());
    }

    /**
     * 获取日期所属的 ISO 周数。
     */
    public static int getWeekNumber(LocalDate date) {
        return date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * 获取指定 ISO week-based year 的最大周数。
     *
     * <p>ISO 定义下，12 月 28 日一定落在该年的最后一个 ISO 周内。</p>
     */
    public static int getMaxWeekNumber(int weekBasedYear) {
        return getWeekNumber(LocalDate.of(weekBasedYear, 12, 28));
    }

    /**
     * 判断指定 year/week 是否是合法的 ISO 周。
     */
    public static boolean isValidWeek(int weekBasedYear, int weekNumber) {
        return weekNumber >= 1 && weekNumber <= getMaxWeekNumber(weekBasedYear);
    }
}
