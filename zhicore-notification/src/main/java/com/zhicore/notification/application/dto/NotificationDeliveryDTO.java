package com.zhicore.notification.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

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
    LocalDateTime lastAttemptAt;
    LocalDateTime nextRetryAt;
    LocalDateTime sentAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
