package com.zhicore.content.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Outbox 手动重试审计记录模型。
 */
@Value
@Builder
public class OutboxRetryAuditRecord {

    String eventId;
    Long operatorId;
    String reason;
    Instant retriedAt;
    String result;
    Instant createdAt;
}
