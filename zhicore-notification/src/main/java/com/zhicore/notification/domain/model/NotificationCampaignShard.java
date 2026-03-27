package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Getter
public class NotificationCampaignShard {

    private final Long id;
    private final Long campaignId;
    private final LocalDateTime afterCreatedAt;
    private final Long afterFollowerId;
    private final Integer batchSize;
    private NotificationCampaignShardStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    private NotificationCampaignShard(Long id,
                                      Long campaignId,
                                      LocalDateTime afterCreatedAt,
                                      Long afterFollowerId,
                                      Integer batchSize,
                                      NotificationCampaignShardStatus status,
                                      LocalDateTime createdAt,
                                      LocalDateTime updatedAt,
                                      LocalDateTime completedAt,
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
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static NotificationCampaignShard create(Long id,
                                                   Long campaignId,
                                                   LocalDateTime afterCreatedAt,
                                                   Long afterFollowerId,
                                                   Integer batchSize) {
        return new NotificationCampaignShard(id, campaignId, afterCreatedAt, afterFollowerId, batchSize,
                NotificationCampaignShardStatus.PENDING, LocalDateTime.now(), LocalDateTime.now(), null, null);
    }

    public static NotificationCampaignShard reconstitute(Long id,
                                                         Long campaignId,
                                                         LocalDateTime afterCreatedAt,
                                                         Long afterFollowerId,
                                                         Integer batchSize,
                                                         NotificationCampaignShardStatus status,
                                                         LocalDateTime createdAt,
                                                         LocalDateTime updatedAt,
                                                         LocalDateTime completedAt,
                                                         String errorMessage) {
        return new NotificationCampaignShard(id, campaignId, afterCreatedAt, afterFollowerId, batchSize,
                status, createdAt, updatedAt, completedAt, errorMessage);
    }

    public void markRunning() {
        this.status = NotificationCampaignShardStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = NotificationCampaignShardStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
        this.completedAt = this.updatedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationCampaignShardStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
