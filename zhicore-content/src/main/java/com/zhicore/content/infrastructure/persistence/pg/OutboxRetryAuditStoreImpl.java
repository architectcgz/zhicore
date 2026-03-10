package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.application.model.OutboxRetryAuditRecord;
import com.zhicore.content.application.port.store.OutboxRetryAuditStore;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxRetryAuditEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxRetryAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Outbox 手动重试审计存储实现。
 */
@Component
@RequiredArgsConstructor
public class OutboxRetryAuditStoreImpl implements OutboxRetryAuditStore {

    private final OutboxRetryAuditMapper outboxRetryAuditMapper;

    @Override
    public long countRecentRetries(String eventId, Instant since) {
        return outboxRetryAuditMapper.countRecentRetries(eventId, since);
    }

    @Override
    public void save(OutboxRetryAuditRecord auditRecord) {
        OutboxRetryAuditEntity entity = new OutboxRetryAuditEntity();
        entity.setEventId(auditRecord.getEventId());
        entity.setOperatorId(auditRecord.getOperatorId());
        entity.setReason(auditRecord.getReason());
        entity.setRetriedAt(auditRecord.getRetriedAt());
        entity.setResult(auditRecord.getResult());
        entity.setCreatedAt(auditRecord.getCreatedAt());
        outboxRetryAuditMapper.insert(entity);
    }
}
