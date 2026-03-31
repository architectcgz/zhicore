package com.zhicore.notification.domain.model;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 发布广播 campaign。
 */
public class NotificationCampaign {

    private final Long campaignId;
    private final String campaignType;
    private final String sourceEventId;
    private final Long authorId;
    private final Long postId;
    private final int audienceEstimate;
    private String status;
    private final String title;
    private final String excerpt;
    private final Instant publishedAt;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;

    private NotificationCampaign(Long campaignId,
                                 String campaignType,
                                 String sourceEventId,
                                 Long authorId,
                                 Long postId,
                                 int audienceEstimate,
                                 String status,
                                 String title,
                                 String excerpt,
                                 Instant publishedAt,
                                 OffsetDateTime createdAt,
                                 OffsetDateTime updatedAt,
                                 OffsetDateTime completedAt,
                                 String errorMessage) {
        this.campaignId = campaignId;
        this.campaignType = campaignType;
        this.sourceEventId = sourceEventId;
        this.authorId = authorId;
        this.postId = postId;
        this.audienceEstimate = audienceEstimate;
        this.status = status;
        this.title = title;
        this.excerpt = excerpt;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }

    public static NotificationCampaign planPostPublished(Long campaignId,
                                                         String sourceEventId,
                                                         Long authorId,
                                                         Long postId,
                                                         int audienceEstimate,
                                                         String title,
                                                         String excerpt,
                                                         Instant publishedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return new NotificationCampaign(
                campaignId,
                "POST_PUBLISHED",
                sourceEventId,
                authorId,
                postId,
                Math.max(audienceEstimate, 0),
                "PLANNED",
                title,
                excerpt,
                publishedAt,
                now,
                now,
                null,
                null
        );
    }

    public static NotificationCampaign reconstitute(Long campaignId,
                                                    String sourceEventId,
                                                    Long authorId,
                                                    Long postId,
                                                    NotificationCampaignStatus status,
                                                    OffsetDateTime createdAt,
                                                    OffsetDateTime updatedAt,
                                                    OffsetDateTime completedAt,
                                                    String errorMessage) {
        return new NotificationCampaign(
                campaignId,
                "POST_PUBLISHED",
                sourceEventId,
                authorId,
                postId,
                0,
                status != null ? status.name() : NotificationCampaignStatus.CREATED.name(),
                null,
                null,
                null,
                createdAt,
                updatedAt,
                completedAt,
                errorMessage
        );
    }

    public void markProcessing() {
        this.status = NotificationCampaignStatus.PROCESSING.name();
        this.updatedAt = OffsetDateTime.now();
        this.completedAt = null;
        this.errorMessage = null;
    }

    public void markCompleted() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = NotificationCampaignStatus.COMPLETED.name();
        this.updatedAt = now;
        this.completedAt = now;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationCampaignStatus.FAILED.name();
        this.updatedAt = OffsetDateTime.now();
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return campaignId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public String getCampaignType() {
        return campaignType;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public Long getPostId() {
        return postId;
    }

    public int getAudienceEstimate() {
        return audienceEstimate;
    }

    public String getStatus() {
        return status;
    }

    public NotificationCampaignStatus getStatusEnum() {
        return NotificationCampaignStatus.valueOf(status);
    }

    public String getTitle() {
        return title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
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
