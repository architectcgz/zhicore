package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 通知持久化对象。
 */
@Data
@TableName("notifications")
public class NotificationPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long recipientId;
    private Integer type;
    private Integer category;
    private String eventCode;
    private String metadata;
    private String notificationType;
    private Long actorId;
    private String targetType;
    private Long targetId;
    private String sourceEventId;
    private String groupKey;
    private String payloadJson;
    private String content;
    private Integer importance;
    private Boolean isRead;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
}
