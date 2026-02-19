package com.blog.user.infrastructure.repository.mapper;

import com.blog.user.infrastructure.repository.po.UserCheckInPO;
import com.blog.user.infrastructure.repository.po.UserCheckInStatsPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;

/**
 * 用户签到 Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface UserCheckInMapper extends BaseMapper<UserCheckInPO> {

    /**
     * 检查指定日期是否已签到
     */
    @Select("SELECT EXISTS(SELECT 1 FROM user_check_ins WHERE user_id = #{userId} AND check_in_date = #{date})")
    boolean existsByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 查询签到统计
     */
    @Select("SELECT * FROM user_check_in_stats WHERE user_id = #{userId}")
    UserCheckInStatsPO selectStatsByUserId(@Param("userId") Long userId);

    /**
     * 插入签到统计
     */
    @Insert("INSERT INTO user_check_in_stats (user_id, total_days, continuous_days, max_continuous_days, last_check_in_date) " +
            "VALUES (#{userId}, #{totalDays}, #{continuousDays}, #{maxContinuousDays}, #{lastCheckInDate})")
    void insertStats(UserCheckInStatsPO stats);

    /**
     * 更新签到统计
     */
    @Update("UPDATE user_check_in_stats SET total_days = #{totalDays}, continuous_days = #{continuousDays}, " +
            "max_continuous_days = #{maxContinuousDays}, last_check_in_date = #{lastCheckInDate} WHERE user_id = #{userId}")
    void updateStats(UserCheckInStatsPO stats);
}
