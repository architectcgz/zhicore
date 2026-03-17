package com.zhicore.content.application.service.command;

import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.content.application.dto.admin.outbox.OutboxRetryResponse;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxRetryAuditRecord;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.OutboxRetryAuditStore;
import com.zhicore.content.infrastructure.messaging.OutboxDispatchTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Outbox 管理端写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxAdminCommandService {

    private final OutboxEventStore outboxEventStore;
    private final OutboxRetryAuditStore outboxRetryAuditStore;
    private final TransactionCommitSignal transactionCommitSignal;
    private final OutboxDispatchTrigger outboxDispatchTrigger;

    @Transactional
    public OutboxRetryResponse retryDead(String eventId, Long operatorId, String reason) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        if (operatorId == null) {
            throw new IllegalArgumentException("operatorId 不能为空");
        }

        OutboxEventRecord eventRecord = outboxEventStore.findByEventId(eventId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Outbox 事件不存在: " + eventId));

        Instant since = Instant.now().minus(10, ChronoUnit.MINUTES);
        long recent = outboxRetryAuditStore.countRecentRetries(eventRecord.getEventId(), since);
        if (recent > 0) {
            throw new TooManyRequestsException("同一事件 10 分钟内仅允许手动重试一次");
        }
        if (eventRecord.getStatus() != OutboxEventRecord.OutboxStatus.DEAD) {
            throw new IllegalArgumentException("仅允许对 DEAD 状态的事件进行手动重试");
        }

        Instant now = Instant.now();
        OutboxEventRecord updatedRecord = eventRecord.withStatus(OutboxEventRecord.OutboxStatus.PENDING)
                .withRetryCount(0)
                .withLastError(null)
                .withNextAttemptAt(now)
                .withClaimedAt(null)
                .withClaimedBy(null)
                .withDispatchedAt(null)
                .withUpdatedAt(now);
        outboxEventStore.update(updatedRecord);
        transactionCommitSignal.afterCommit(outboxDispatchTrigger::signal);

        outboxRetryAuditStore.save(OutboxRetryAuditRecord.builder()
                .eventId(eventRecord.getEventId())
                .operatorId(operatorId)
                .reason(reason)
                .retriedAt(now)
                .result("ACCEPTED")
                .createdAt(now)
                .build());

        log.info("Outbox dead-letter retry accepted: eventId={}, operatorId={}", eventRecord.getEventId(), operatorId);
        return OutboxRetryResponse.builder()
                .eventId(eventRecord.getEventId())
                .status(updatedRecord.getStatus().name())
                .build();
    }
}
