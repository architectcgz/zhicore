package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户通知偏好持久化对象。
 */
@Data
@TableName("notification_user_preference")
public class NotificationUserPreferencePO {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Boolean likeEnabled;

    private Boolean commentEnabled;

    private Boolean followEnabled;

    private Boolean replyEnabled;

    private Boolean systemEnabled;

    private Boolean publishEnabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
