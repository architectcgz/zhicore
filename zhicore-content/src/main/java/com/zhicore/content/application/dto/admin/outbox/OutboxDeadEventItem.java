package com.zhicore.content.application.dto.admin.outbox;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Outbox 死信事件条目（管理端）
 */
@Data
@Builder
public class OutboxDeadEventItem {

    private String eventId;
    private String eventType;
    private Long aggregateId;
    private Long aggregateVersion;
    private Integer retryCount;
    private String lastError;

    private Instant occurredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
