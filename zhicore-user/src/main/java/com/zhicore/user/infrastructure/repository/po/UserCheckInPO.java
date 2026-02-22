package com.zhicore.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 用户签到持久化对象
 * 使用复合主键 (userId, checkInDate)
 *
 * @author ZhiCore Team
 */
@Data
@TableName("user_check_ins")
public class UserCheckInPO {

    private Long userId;

    private LocalDate checkInDate;

    private OffsetDateTime createdAt;
}
