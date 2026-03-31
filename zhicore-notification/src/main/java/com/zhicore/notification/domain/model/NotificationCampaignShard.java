package com.zhicore.notification.domain.model;

import java.time.OffsetDateTime;

/**
 * 发布广播 shard。
 */
public class NotificationCampaignShard {

    private final Long shardId;
    private final Long campaignId;
    private final Long startCursor;
    private final Long endCursor;
    private final int shardSize;
    private final OffsetDateTime afterCreatedAt;
    private final Long afterFollowerId;
    private final int batchSize;
    private String status;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;

    private NotificationCampaignShard(Long shardId,
                                      Long campaignId,
                                      Long startCursor,
                                      Long endCursor,
                                      int shardSize,
                                      OffsetDateTime afterCreatedAt,
                                      Long afterFollowerId,
                                      int batchSize,
                                      String status,
                                      OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt,
                                      OffsetDateTime completedAt,
                                      String errorMessage) {
        this.shardId = shardId;
        this.campaignId = campaignId;
        this.startCursor = startCursor;
        this.endCursor = endCursor;
        this.shardSize = shardSize;
        this.afterCreatedAt = afterCreatedAt;
        this.afterFollowerId = afterFollowerId;
        this.batchSize = batchSize;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static NotificationCampaignShard firstShard(Long shardId, Long campaignId, int shardSize) {
        OffsetDateTime now = OffsetDateTime.now();
        return new NotificationCampaignShard(
                shardId,
                campaignId,
                0L,
                null,
                Math.max(shardSize, 0),
                null,
                null,
                Math.max(shardSize, 0),
                "PLANNED",
                now,
                now,
                null,
                null
        );
    }

    public static NotificationCampaignShard create(Long shardId,
                                                   Long campaignId,
                                                   OffsetDateTime afterCreatedAt,
                                                   Long afterFollowerId,
                                                   int batchSize) {
        OffsetDateTime now = OffsetDateTime.now();
        return new NotificationCampaignShard(
                shardId,
                campaignId,
                null,
                null,
                Math.max(batchSize, 0),
                afterCreatedAt,
                afterFollowerId,
                Math.max(batchSize, 0),
                NotificationCampaignShardStatus.PENDING.name(),
                now,
                now,
                null,
                null
        );
    }

    public static NotificationCampaignShard reconstitute(Long shardId,
                                                         Long campaignId,
                                                         OffsetDateTime afterCreatedAt,
                                                         Long afterFollowerId,
                                                         Integer batchSize,
                                                         NotificationCampaignShardStatus status,
                                                         OffsetDateTime createdAt,
                                                         OffsetDateTime updatedAt,
                                                         OffsetDateTime completedAt,
                                                         String errorMessage) {
        int resolvedBatchSize = batchSize == null ? 0 : batchSize;
        return new NotificationCampaignShard(
                shardId,
                campaignId,
                null,
                null,
                resolvedBatchSize,
                afterCreatedAt,
                afterFollowerId,
                resolvedBatchSize,
                status != null ? status.name() : NotificationCampaignShardStatus.PENDING.name(),
                createdAt,
                updatedAt,
                completedAt,
                errorMessage
        );
    }

    public void markCompleted() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = NotificationCampaignShardStatus.COMPLETED.name();
        this.updatedAt = now;
        this.completedAt = now;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationCampaignShardStatus.FAILED.name();
        this.updatedAt = OffsetDateTime.now();
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return shardId;
    }

    public Long getShardId() {
        return shardId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getStartCursor() {
        return startCursor;
    }

    public Long getEndCursor() {
        return endCursor;
    }

    public int getShardSize() {
        return shardSize;
    }

    public OffsetDateTime getAfterCreatedAt() {
        return afterCreatedAt;
    }

    public Long getAfterFollowerId() {
        return afterFollowerId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public String getStatus() {
        return status;
    }

    public NotificationCampaignShardStatus getStatusEnum() {
        return NotificationCampaignShardStatus.valueOf(status);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
