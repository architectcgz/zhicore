package com.zhicore.user.application.port.store;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 用户签到缓存存储端口。
 *
 * 封装 Redis Bitmap 的访问细节，
 * 避免应用层直接依赖底层位图命令。
 */
public interface CheckInStore {

    /**
     * 检查指定日期是否已签到。
     *
     * @param userId 用户ID
     * @param date 日期
     * @return 是否已签到
     */
    boolean hasCheckedIn(Long userId, LocalDate date);

    /**
     * 标记指定日期已签到。
     *
     * @param userId 用户ID
     * @param date 日期
     */
    void markCheckedIn(Long userId, LocalDate date);

    /**
     * 读取用户当月签到位图。
     *
     * @param userId 用户ID
     * @param yearMonth 年月
     * @return 位图值
     */
    long getMonthlyBitmap(Long userId, YearMonth yearMonth);
}
