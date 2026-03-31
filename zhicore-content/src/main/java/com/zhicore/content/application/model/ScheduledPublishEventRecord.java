package com.zhicore.content.application.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.OffsetDateTime;

/**
 * 定时发布事件记录模型。
 */
@Value
@With
@Builder
public class ScheduledPublishEventRecord {

    Long id;
    String eventId;
    String triggerEventId;
    Long postId;
    OffsetDateTime scheduledAt;
    OffsetDateTime nextAttemptAt;
    ScheduledPublishStatus status;
    Integer rescheduleRetryCount;
    Integer publishRetryCount;
    OffsetDateTime claimedAt;
    String claimedBy;
    String lastError;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;

    public enum ScheduledPublishStatus {
        PENDING,
        PROCESSING,
        FAILED,
        SUCCEEDED,
        DEAD
    }
}
