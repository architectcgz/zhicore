package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;

@Getter
public class NotificationCampaign {

    private final Long id;
    private final String triggerEventId;
    private final Long postId;
    private final Long authorId;
    private NotificationCampaignStatus status;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;

    private NotificationCampaign(Long id,
                                 String triggerEventId,
                                 Long postId,
                                 Long authorId,
                                 NotificationCampaignStatus status,
                                 OffsetDateTime createdAt,
                                 OffsetDateTime updatedAt,
                                 OffsetDateTime completedAt,
                                 String errorMessage) {
        Assert.notNull(id, "campaignId不能为空");
        Assert.hasText(triggerEventId, "triggerEventId不能为空");
        Assert.notNull(postId, "postId不能为空");
        Assert.notNull(authorId, "authorId不能为空");
        Assert.notNull(status, "status不能为空");
        this.id = id;
        this.triggerEventId = triggerEventId;
        this.postId = postId;
        this.authorId = authorId;
        this.status = status;
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static NotificationCampaign create(Long id, String triggerEventId, Long postId, Long authorId) {
        return new NotificationCampaign(id, triggerEventId, postId, authorId,
                NotificationCampaignStatus.CREATED, OffsetDateTime.now(), OffsetDateTime.now(), null, null);
    }

    public static NotificationCampaign reconstitute(Long id,
                                                    String triggerEventId,
                                                    Long postId,
                                                    Long authorId,
                                                    NotificationCampaignStatus status,
                                                    OffsetDateTime createdAt,
                                                    OffsetDateTime updatedAt,
                                                    OffsetDateTime completedAt,
                                                    String errorMessage) {
        return new NotificationCampaign(id, triggerEventId, postId, authorId,
                status, createdAt, updatedAt, completedAt, errorMessage);
    }

    public void markProcessing() {
        this.status = NotificationCampaignStatus.PROCESSING;
        this.updatedAt = OffsetDateTime.now();
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = NotificationCampaignStatus.COMPLETED;
        this.updatedAt = OffsetDateTime.now();
        this.completedAt = this.updatedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationCampaignStatus.FAILED;
        this.updatedAt = OffsetDateTime.now();
        this.errorMessage = errorMessage;
    }
}
