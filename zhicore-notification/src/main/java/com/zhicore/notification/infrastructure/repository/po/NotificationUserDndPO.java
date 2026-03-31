package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户免打扰配置持久化对象。
 */
@Data
@TableName("notification_user_dnd")
public class NotificationUserDndPO {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Boolean enabled;

    private String startTime;

    private String endTime;

    private String timezone;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
