package com.zhicore.content.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.admin.outbox.OutboxDeadEventItem;
import com.zhicore.content.application.dto.admin.outbox.OutboxDeadPageResponse;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 管理端查询服务。
 */
@Service
@RequiredArgsConstructor
public class OutboxAdminQueryService {

    private final OutboxEventStore outboxEventStore;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.LIST_FAILED_OUTBOX,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleListDeadOutboxBlocked"
    )
    public OutboxDeadPageResponse listDead(int page, int size, String eventType) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        PageResult<OutboxEventRecord> result = outboxEventStore.findDead(safePage, safeSize, normalizeEventType(eventType));
        List<OutboxDeadEventItem> items = result.getRecords().stream()
                .map(e -> OutboxDeadEventItem.builder()
                        .eventId(e.getEventId())
                        .eventType(e.getEventType())
                        .aggregateId(e.getAggregateId())
                        .aggregateVersion(e.getAggregateVersion())
                        .retryCount(e.getRetryCount())
                        .lastError(e.getLastError())
                        .occurredAt(e.getOccurredAt())
                        .createdAt(e.getCreatedAt())
                        .updatedAt(e.getUpdatedAt())
                        .build())
                .toList();

        return OutboxDeadPageResponse.builder()
                .page((int) result.getCurrent())
                .size((int) result.getSize())
                .total(result.getTotal())
                .items(items)
                .build();
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        return eventType.trim();
    }
}
