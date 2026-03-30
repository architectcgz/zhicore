package com.zhicore.notification.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

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
    Instant lastAttemptAt;
    Instant nextRetryAt;
    Instant sentAt;
    Instant createdAt;
    Instant updatedAt;
}
