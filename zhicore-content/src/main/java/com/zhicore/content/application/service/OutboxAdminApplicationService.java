package com.zhicore.content.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxRetryAuditEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxRetryAuditMapper;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxFailedEventItem;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxFailedPageResponse;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxRetryResponse;
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

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxRetryAuditMapper outboxRetryAuditMapper;

    @Transactional(readOnly = true)
    public OutboxFailedPageResponse listFailed(int page, int size, String eventType) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        Page<OutboxEventEntity> mpPage = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<OutboxEventEntity> wrapper = new LambdaQueryWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getStatus, OutboxEventEntity.OutboxStatus.FAILED)
                .orderByDesc(OutboxEventEntity::getUpdatedAt)
                .orderByDesc(OutboxEventEntity::getCreatedAt);

        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(OutboxEventEntity::getEventType, eventType.trim());
        }

        Page<OutboxEventEntity> result = outboxEventMapper.selectPage(mpPage, wrapper);
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
                .page(safePage)
                .size(safeSize)
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

        OutboxEventEntity entity = outboxEventMapper.selectOne(
                new LambdaQueryWrapper<OutboxEventEntity>()
                        .eq(OutboxEventEntity::getEventId, eventId.trim())
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new ResourceNotFoundException("Outbox 事件不存在: " + eventId);
        }

        Instant since = Instant.now().minus(10, ChronoUnit.MINUTES);
        long recent = outboxRetryAuditMapper.countRecentRetries(entity.getEventId(), since);
        if (recent > 0) {
            throw new TooManyRequestsException("同一事件 10 分钟内仅允许手动重试一次");
        }

        // 仅允许对 FAILED 事件进行人工重试，避免误操作
        if (entity.getStatus() != OutboxEventEntity.OutboxStatus.FAILED) {
            throw new IllegalArgumentException("仅允许对 FAILED 状态的事件进行手动重试");
        }

        Instant now = Instant.now();
        entity.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
        entity.setRetryCount(0);
        entity.setLastError(null);
        entity.setDispatchedAt(null);
        entity.setUpdatedAt(now);
        outboxEventMapper.updateById(entity);

        OutboxRetryAuditEntity audit = new OutboxRetryAuditEntity();
        audit.setEventId(entity.getEventId());
        audit.setOperatorId(operatorId);
        audit.setReason(reason);
        audit.setRetriedAt(now);
        audit.setResult("ACCEPTED");
        audit.setCreatedAt(now);
        outboxRetryAuditMapper.insert(audit);

        log.info("Outbox manual retry accepted: eventId={}, operatorId={}", entity.getEventId(), operatorId);

        return OutboxRetryResponse.builder()
                .eventId(entity.getEventId())
                .status(entity.getStatus().name())
                .build();
    }
}

