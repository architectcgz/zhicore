package com.zhicore.user.domain.model;

import com.zhicore.common.util.DateTimeUtils;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDate;

/**
 * 用户签到统计值对象
 *
 * @author ZhiCore Team
 */
@Getter
public class UserCheckInStats {

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 总签到天数
     */
    private int totalDays;

    /**
     * 连续签到天数
     */
    private int continuousDays;

    /**
     * 最大连续签到天数
     */
    private int maxContinuousDays;

    /**
     * 最后签到日期
     */
    private LocalDate lastCheckInDate;

    private UserCheckInStats(Long userId, int totalDays, int continuousDays, 
                             int maxContinuousDays, LocalDate lastCheckInDate) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        this.userId = userId;
        this.totalDays = totalDays;
        this.continuousDays = continuousDays;
        this.maxContinuousDays = maxContinuousDays;
        this.lastCheckInDate = lastCheckInDate;
    }

    /**
     * 创建新的签到统计
     */
    public static UserCheckInStats create(Long userId) {
        return new UserCheckInStats(userId, 0, 0, 0, null);
    }

    /**
     * 从持久化恢复
     */
    public static UserCheckInStats reconstitute(Long userId, int totalDays, int continuousDays,
                                                 int maxContinuousDays, LocalDate lastCheckInDate) {
        return new UserCheckInStats(userId, totalDays, continuousDays, maxContinuousDays, lastCheckInDate);
    }

    /**
     * 记录签到
     *
     * @param checkInDate 签到日期
     */
    public void recordCheckIn(LocalDate checkInDate) {
        this.totalDays++;
        
        // 计算连续签到
        if (lastCheckInDate == null) {
            this.continuousDays = 1;
        } else if (checkInDate.minusDays(1).equals(lastCheckInDate)) {
            // 连续签到
            this.continuousDays++;
        } else {
            // 断签，重新计算
            this.continuousDays = 1;
        }
        
        // 更新最大连续签到天数
        if (this.continuousDays > this.maxContinuousDays) {
            this.maxContinuousDays = this.continuousDays;
        }
        
        this.lastCheckInDate = checkInDate;
    }

    /**
     * 检查今天是否已签到（基于业务时区）
     */
    public boolean hasCheckedInToday() {
        return lastCheckInDate != null && lastCheckInDate.equals(DateTimeUtils.businessDate());
    }

    /**
     * 检查指定日期是否已签到
     */
    public boolean hasCheckedIn(LocalDate date) {
        return lastCheckInDate != null && lastCheckInDate.equals(date);
    }
}
