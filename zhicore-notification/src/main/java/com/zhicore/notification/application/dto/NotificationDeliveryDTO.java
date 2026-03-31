package com.zhicore.notification.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class NotificationDeliveryDTO {
    Long id;
    Long campaignId;
    Long recipientId;
    Long notificationId;
    String channel;
    String status;
    String skipReason;
    String failureReason;
    Integer retryCount;
    OffsetDateTime lastAttemptAt;
    OffsetDateTime nextRetryAt;
    OffsetDateTime sentAt;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
