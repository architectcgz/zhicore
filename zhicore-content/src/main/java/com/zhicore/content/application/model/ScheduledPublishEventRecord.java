package com.zhicore.content.application.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;

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
    LocalDateTime scheduledAt;
    LocalDateTime nextAttemptAt;
    ScheduledPublishStatus status;
    Integer rescheduleRetryCount;
    Integer publishRetryCount;
    LocalDateTime claimedAt;
    String claimedBy;
    String lastError;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public enum ScheduledPublishStatus {
        PENDING,
        PROCESSING,
        FAILED,
        SUCCEEDED,
        DEAD
    }
}
