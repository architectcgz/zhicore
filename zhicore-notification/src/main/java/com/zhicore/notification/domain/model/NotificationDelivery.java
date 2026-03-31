package com.zhicore.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDelivery {

    private Long deliveryId;
    private Long recipientId;
    private Long campaignId;
    private Long notificationId;
    private NotificationChannel channel;
    private NotificationType notificationType;
    private String dedupeKey;
    private String deliveryStatus;
    private String skipReason;
    private String failureReason;
    private Integer retryCount;
    private Instant lastAttemptAt;
    private Instant nextRetryAt;
    private Instant sentAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static NotificationDelivery planned(Long deliveryId,
                                               Long recipientId,
                                               Long campaignId,
                                               NotificationChannel channel,
                                               NotificationType notificationType,
                                               String dedupeKey) {
        return planned(deliveryId, recipientId, campaignId, null, channel, notificationType, dedupeKey);
    }

    public static NotificationDelivery planned(Long deliveryId,
                                               Long recipientId,
                                               Long campaignId,
                                               Long notificationId,
                                               NotificationChannel channel,
                                               NotificationType notificationType,
                                               String dedupeKey) {
        Instant now = Instant.now();
        return NotificationDelivery.builder()
                .deliveryId(deliveryId)
                .recipientId(recipientId)
                .campaignId(campaignId)
                .notificationId(notificationId)
                .channel(channel)
                .notificationType(notificationType)
                .dedupeKey(dedupeKey)
                .deliveryStatus("PLANNED")
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static NotificationDelivery digestPending(Long deliveryId,
                                                     Long recipientId,
                                                     Long campaignId,
                                                     String dedupeKey,
                                                     String reason) {
        Instant now = Instant.now();
        return NotificationDelivery.builder()
                .deliveryId(deliveryId)
                .recipientId(recipientId)
                .campaignId(campaignId)
                .channel(NotificationChannel.IN_APP)
                .notificationType(NotificationType.POST_PUBLISHED_DIGEST)
                .dedupeKey(dedupeKey)
                .deliveryStatus("DIGEST_PENDING")
                .skipReason(reason)
                .failureReason(reason)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static NotificationDelivery skipped(Long deliveryId,
                                               Long recipientId,
                                               Long campaignId,
                                               NotificationChannel channel,
                                               NotificationType notificationType,
                                               String dedupeKey,
                                               String reason) {
        Instant now = Instant.now();
        return NotificationDelivery.builder()
                .deliveryId(deliveryId)
                .recipientId(recipientId)
                .campaignId(campaignId)
                .channel(channel)
                .notificationType(notificationType)
                .dedupeKey(dedupeKey)
                .deliveryStatus("SKIPPED")
                .skipReason(reason)
                .failureReason(reason)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void bindNotification(Long notificationId, String deliveryStatus) {
        this.notificationId = notificationId;
        this.deliveryStatus = deliveryStatus;
        this.updatedAt = Instant.now();
    }

    public void markSent(Long notificationId) {
        Instant now = Instant.now();
        this.notificationId = notificationId;
        this.deliveryStatus = "SENT";
        this.skipReason = null;
        this.failureReason = null;
        this.lastAttemptAt = now;
        this.nextRetryAt = null;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void markSkipped(String reason) {
        markSkipped("SKIPPED", reason, this.notificationId, null);
    }

    public void markSkipped(String deliveryStatus, String reason) {
        markSkipped(deliveryStatus, reason, this.notificationId, null);
    }

    public void markSkipped(String deliveryStatus, String reason, Long notificationId, Instant nextRetryAt) {
        Instant now = Instant.now();
        this.notificationId = notificationId;
        this.deliveryStatus = deliveryStatus;
        this.skipReason = reason;
        this.failureReason = reason;
        this.lastAttemptAt = now;
        this.nextRetryAt = nextRetryAt;
        this.sentAt = null;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant nextRetryAt) {
        markFailed("FAILED", reason, nextRetryAt);
    }

    public void markFailed(String deliveryStatus, String reason, Instant nextRetryAt) {
        Instant now = Instant.now();
        this.deliveryStatus = deliveryStatus;
        this.skipReason = null;
        this.failureReason = reason;
        this.retryCount = retryCount == null ? 1 : retryCount + 1;
        this.lastAttemptAt = now;
        this.nextRetryAt = nextRetryAt;
        this.sentAt = null;
        this.updatedAt = now;
    }
}
