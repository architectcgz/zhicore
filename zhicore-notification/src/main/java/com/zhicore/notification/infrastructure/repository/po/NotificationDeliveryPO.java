package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("notification_delivery")
public class NotificationDeliveryPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long campaignId;

    private Long shardId;

    private Long recipientId;

    private String channel;

    private String dedupeKey;

    private String status;

    private Long notificationId;

    private String skipReason;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime sentAt;
}
