package com.zhicore.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户签到统计持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("user_check_in_stats")
public class UserCheckInStatsPO {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private Integer totalDays;

    private Integer continuousDays;

    private Integer maxContinuousDays;

    private LocalDate lastCheckInDate;
}
