package com.zhicore.content.application.port.store;

import com.zhicore.content.application.model.OutboxRetryAuditRecord;

import java.time.Instant;

/**
 * Outbox 手动重试审计存储端口。
 */
public interface OutboxRetryAuditStore {

    long countRecentRetries(String eventId, Instant since);

    void save(OutboxRetryAuditRecord auditRecord);
}
