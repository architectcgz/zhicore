package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;

@Getter
public class NotificationCampaignShard {

    private final Long id;
    private final Long campaignId;
    private final OffsetDateTime afterCreatedAt;
    private final Long afterFollowerId;
    private final Integer batchSize;
    private NotificationCampaignShardStatus status;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;

    private NotificationCampaignShard(Long id,
                                      Long campaignId,
                                      OffsetDateTime afterCreatedAt,
                                      Long afterFollowerId,
                                      Integer batchSize,
                                      NotificationCampaignShardStatus status,
                                      OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt,
                                      OffsetDateTime completedAt,
                                      String errorMessage) {
        Assert.notNull(id, "shardId不能为空");
        Assert.notNull(campaignId, "campaignId不能为空");
        Assert.notNull(batchSize, "batchSize不能为空");
        Assert.notNull(status, "status不能为空");
        this.id = id;
        this.campaignId = campaignId;
        this.afterCreatedAt = afterCreatedAt;
        this.afterFollowerId = afterFollowerId;
        this.batchSize = batchSize;
        this.status = status;
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static NotificationCampaignShard create(Long id,
                                                   Long campaignId,
                                                   OffsetDateTime afterCreatedAt,
                                                   Long afterFollowerId,
                                                   Integer batchSize) {
        return new NotificationCampaignShard(id, campaignId, afterCreatedAt, afterFollowerId, batchSize,
                NotificationCampaignShardStatus.PENDING, OffsetDateTime.now(), OffsetDateTime.now(), null, null);
    }

    public static NotificationCampaignShard reconstitute(Long id,
                                                         Long campaignId,
                                                         OffsetDateTime afterCreatedAt,
                                                         Long afterFollowerId,
                                                         Integer batchSize,
                                                         NotificationCampaignShardStatus status,
                                                         OffsetDateTime createdAt,
                                                         OffsetDateTime updatedAt,
                                                         OffsetDateTime completedAt,
                                                         String errorMessage) {
        return new NotificationCampaignShard(id, campaignId, afterCreatedAt, afterFollowerId, batchSize,
                status, createdAt, updatedAt, completedAt, errorMessage);
    }

    public void markRunning() {
        this.status = NotificationCampaignShardStatus.RUNNING;
        this.updatedAt = OffsetDateTime.now();
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = NotificationCampaignShardStatus.COMPLETED;
        this.updatedAt = OffsetDateTime.now();
        this.completedAt = this.updatedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationCampaignShardStatus.FAILED;
        this.updatedAt = OffsetDateTime.now();
        this.errorMessage = errorMessage;
    }
}
