package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Getter
public class NotificationCampaign {

    private final Long id;
    private final String triggerEventId;
    private final Long postId;
    private final Long authorId;
    private NotificationCampaignStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    private NotificationCampaign(Long id,
                                 String triggerEventId,
                                 Long postId,
                                 Long authorId,
                                 NotificationCampaignStatus status,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt,
                                 LocalDateTime completedAt,
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
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static NotificationCampaign create(Long id, String triggerEventId, Long postId, Long authorId) {
        return new NotificationCampaign(id, triggerEventId, postId, authorId,
                NotificationCampaignStatus.CREATED, LocalDateTime.now(), LocalDateTime.now(), null, null);
    }

    public static NotificationCampaign reconstitute(Long id,
                                                    String triggerEventId,
                                                    Long postId,
                                                    Long authorId,
                                                    NotificationCampaignStatus status,
                                                    LocalDateTime createdAt,
                                                    LocalDateTime updatedAt,
                                                    LocalDateTime completedAt,
                                                    String errorMessage) {
        return new NotificationCampaign(id, triggerEventId, postId, authorId,
                status, createdAt, updatedAt, completedAt, errorMessage);
    }

    public void markProcessing() {
        this.status = NotificationCampaignStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = NotificationCampaignStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
        this.completedAt = this.updatedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationCampaignStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
