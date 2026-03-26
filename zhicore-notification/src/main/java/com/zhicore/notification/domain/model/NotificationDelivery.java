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
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static NotificationDelivery planned(Long deliveryId,
                                               Long recipientId,
                                               Long campaignId,
                                               NotificationChannel channel,
                                               NotificationType notificationType,
                                               String dedupeKey) {
        Instant now = Instant.now();
        return NotificationDelivery.builder()
                .deliveryId(deliveryId)
                .recipientId(recipientId)
                .campaignId(campaignId)
                .channel(channel)
                .notificationType(notificationType)
                .dedupeKey(dedupeKey)
                .deliveryStatus("PLANNED")
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
                .failureReason(reason)
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
                .failureReason(reason)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
