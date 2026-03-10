package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.admin.outbox.OutboxFailedEventItem;
import com.zhicore.content.application.dto.admin.outbox.OutboxFailedPageResponse;
import com.zhicore.content.application.dto.admin.outbox.OutboxRetryResponse;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxRetryAuditRecord;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.OutboxRetryAuditStore;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Outbox 管理端应用服务（R14）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxAdminApplicationService {

    private final OutboxEventStore outboxEventStore;
    private final OutboxRetryAuditStore outboxRetryAuditStore;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.LIST_FAILED_OUTBOX,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleListFailedOutboxBlocked"
    )
    public OutboxFailedPageResponse listFailed(int page, int size, String eventType) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        PageResult<OutboxEventRecord> result = outboxEventStore.findFailed(safePage, safeSize, normalizeEventType(eventType));
        List<OutboxFailedEventItem> items = result.getRecords().stream()
                .map(e -> OutboxFailedEventItem.builder()
                        .eventId(e.getEventId())
                        .eventType(e.getEventType())
                        .aggregateId(e.getAggregateId())
                        .aggregateVersion(e.getAggregateVersion())
                        .retryCount(e.getRetryCount())
                        .lastError(e.getLastError())
                        .occurredAt(e.getOccurredAt())
                        .createdAt(e.getCreatedAt())
                        .updatedAt(e.getUpdatedAt())
                        .build()
                )
                .toList();

        return OutboxFailedPageResponse.builder()
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .total(result.getTotal())
                .items(items)
                .build();
    }

    @Transactional
    public OutboxRetryResponse retryFailed(String eventId, Long operatorId, String reason) {
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

        // 仅允许对 FAILED 事件进行人工重试，避免误操作
        if (eventRecord.getStatus() != OutboxEventRecord.OutboxStatus.FAILED) {
            throw new IllegalArgumentException("仅允许对 FAILED 状态的事件进行手动重试");
        }

        Instant now = Instant.now();
        OutboxEventRecord updatedRecord = eventRecord.withStatus(OutboxEventRecord.OutboxStatus.PENDING)
                .withRetryCount(0)
                .withLastError(null)
                .withDispatchedAt(null)
                .withUpdatedAt(now);
        outboxEventStore.update(updatedRecord);

        outboxRetryAuditStore.save(OutboxRetryAuditRecord.builder()
                .eventId(eventRecord.getEventId())
                .operatorId(operatorId)
                .reason(reason)
                .retriedAt(now)
                .result("ACCEPTED")
                .createdAt(now)
                .build());

        log.info("Outbox manual retry accepted: eventId={}, operatorId={}", eventRecord.getEventId(), operatorId);

        return OutboxRetryResponse.builder()
                .eventId(eventRecord.getEventId())
                .status(updatedRecord.getStatus().name())
                .build();
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        return eventType.trim();
    }
}
