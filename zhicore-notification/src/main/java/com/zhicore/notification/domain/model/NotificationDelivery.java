package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Getter
public class NotificationDelivery {

    private final Long id;
    private final Long campaignId;
    private final Long shardId;
    private final Long recipientId;
    private final String channel;
    private final String dedupeKey;
    private NotificationDeliveryStatus status;
    private Long notificationId;
    private String skipReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime sentAt;

    private NotificationDelivery(Long id,
                                 Long campaignId,
                                 Long shardId,
                                 Long recipientId,
                                 String channel,
                                 String dedupeKey,
                                 NotificationDeliveryStatus status,
                                 Long notificationId,
                                 String skipReason,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt,
                                 LocalDateTime sentAt) {
        Assert.notNull(id, "deliveryId不能为空");
        Assert.notNull(campaignId, "campaignId不能为空");
        Assert.notNull(shardId, "shardId不能为空");
        Assert.notNull(recipientId, "recipientId不能为空");
        Assert.hasText(channel, "channel不能为空");
        Assert.hasText(dedupeKey, "dedupeKey不能为空");
        Assert.notNull(status, "status不能为空");
        this.id = id;
        this.campaignId = campaignId;
        this.shardId = shardId;
        this.recipientId = recipientId;
        this.channel = channel;
        this.dedupeKey = dedupeKey;
        this.status = status;
        this.notificationId = notificationId;
        this.skipReason = skipReason;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.sentAt = sentAt;
    }

    public static NotificationDelivery pending(Long id,
                                               Long campaignId,
                                               Long shardId,
                                               Long recipientId,
                                               String channel,
                                               String dedupeKey) {
        return new NotificationDelivery(id, campaignId, shardId, recipientId, channel, dedupeKey,
                NotificationDeliveryStatus.PENDING, null, null, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static NotificationDelivery reconstitute(Long id,
                                                    Long campaignId,
                                                    Long shardId,
                                                    Long recipientId,
                                                    String channel,
                                                    String dedupeKey,
                                                    NotificationDeliveryStatus status,
                                                    Long notificationId,
                                                    String skipReason,
                                                    LocalDateTime createdAt,
                                                    LocalDateTime updatedAt,
                                                    LocalDateTime sentAt) {
        return new NotificationDelivery(id, campaignId, shardId, recipientId, channel, dedupeKey,
                status, notificationId, skipReason, createdAt, updatedAt, sentAt);
    }

    public void markSent(Long notificationId) {
        this.status = NotificationDeliveryStatus.SENT;
        this.notificationId = notificationId;
        this.skipReason = null;
        this.updatedAt = LocalDateTime.now();
        this.sentAt = this.updatedAt;
    }

    public void markSkipped(String skipReason) {
        this.status = NotificationDeliveryStatus.SKIPPED;
        this.skipReason = skipReason;
        this.updatedAt = LocalDateTime.now();
    }
}
