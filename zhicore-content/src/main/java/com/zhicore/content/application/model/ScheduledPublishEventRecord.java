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
    Long postId;
    LocalDateTime scheduledAt;
    ScheduledPublishStatus status;
    Integer rescheduleRetryCount;
    Integer publishRetryCount;
    LocalDateTime lastEnqueueAt;
    String lastError;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public enum ScheduledPublishStatus {
        PENDING,
        SCHEDULED_PENDING,
        PUBLISHED,
        FAILED
    }
}
