package com.zhicore.content.application.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;

/**
 * Outbox 事件记录模型。
 */
@Value
@With
@Builder
public class OutboxEventRecord {

    Long id;
    String eventId;
    String eventType;
    Long aggregateId;
    Long aggregateVersion;
    Integer schemaVersion;
    String payload;
    Instant occurredAt;
    Instant createdAt;
    Instant updatedAt;
    Instant nextAttemptAt;
    Instant claimedAt;
    String claimedBy;
    Instant dispatchedAt;
    Integer retryCount;
    String lastError;
    OutboxStatus status;

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED,
        DEAD
    }
}
