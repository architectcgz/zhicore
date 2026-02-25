package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.UserCheckIn;
import com.zhicore.user.domain.model.UserCheckInStats;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 用户签到仓储接口
 *
 * @author ZhiCore Team
 */
public interface UserCheckInRepository {

    /**
     * 保存签到记录
     *
     * @param checkIn 签到记录
     */
    void save(UserCheckIn checkIn);

    /**
     * 检查指定日期是否已签到
     *
     * @param userId 用户ID
     * @param date 日期
     * @return 是否已签到
     */
    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    /**
     * 获取用户签到统计
     *
     * @param userId 用户ID
     * @return 签到统计
     */
    Optional<UserCheckInStats> findStatsByUserId(Long userId);

    /**
     * 保存或更新签到统计
     *
     * @param stats 签到统计
     */
    void saveOrUpdateStats(UserCheckInStats stats);
}
