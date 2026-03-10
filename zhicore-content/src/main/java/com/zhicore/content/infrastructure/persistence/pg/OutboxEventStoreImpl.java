package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Outbox 事件存储实现。
 */
@Component
@RequiredArgsConstructor
public class OutboxEventStoreImpl implements OutboxEventStore {

    private final OutboxEventMapper outboxEventMapper;

    @Override
    public void save(OutboxEventRecord eventRecord) {
        outboxEventMapper.insert(toEntity(eventRecord));
    }

    @Override
    public PageResult<OutboxEventRecord> findFailed(int page, int size, String eventType) {
        Page<OutboxEventEntity> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<OutboxEventEntity> wrapper = new LambdaQueryWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getStatus, OutboxEventEntity.OutboxStatus.FAILED)
                .orderByDesc(OutboxEventEntity::getUpdatedAt)
                .orderByDesc(OutboxEventEntity::getCreatedAt);

        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(OutboxEventEntity::getEventType, eventType.trim());
        }

        Page<OutboxEventEntity> result = outboxEventMapper.selectPage(mpPage, wrapper);
        return PageResult.of(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toRecord).toList()
        );
    }

    @Override
    public Optional<OutboxEventRecord> findByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(outboxEventMapper.selectOne(
                new LambdaQueryWrapper<OutboxEventEntity>()
                        .eq(OutboxEventEntity::getEventId, eventId.trim())
                        .last("LIMIT 1")
        )).map(this::toRecord);
    }

    @Override
    public void update(OutboxEventRecord eventRecord) {
        outboxEventMapper.updateById(toEntity(eventRecord));
    }

    private OutboxEventRecord toRecord(OutboxEventEntity entity) {
        return OutboxEventRecord.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .aggregateId(entity.getAggregateId())
                .aggregateVersion(entity.getAggregateVersion())
                .schemaVersion(entity.getSchemaVersion())
                .payload(entity.getPayload())
                .occurredAt(entity.getOccurredAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .dispatchedAt(entity.getDispatchedAt())
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .status(toRecordStatus(entity.getStatus()))
                .build();
    }

    private OutboxEventEntity toEntity(OutboxEventRecord eventRecord) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(eventRecord.getId());
        entity.setEventId(eventRecord.getEventId());
        entity.setEventType(eventRecord.getEventType());
        entity.setAggregateId(eventRecord.getAggregateId());
        entity.setAggregateVersion(eventRecord.getAggregateVersion());
        entity.setSchemaVersion(eventRecord.getSchemaVersion());
        entity.setPayload(eventRecord.getPayload());
        entity.setOccurredAt(eventRecord.getOccurredAt());
        entity.setCreatedAt(eventRecord.getCreatedAt());
        entity.setUpdatedAt(eventRecord.getUpdatedAt());
        entity.setDispatchedAt(eventRecord.getDispatchedAt());
        entity.setRetryCount(eventRecord.getRetryCount());
        entity.setLastError(eventRecord.getLastError());
        entity.setStatus(toEntityStatus(eventRecord.getStatus()));
        return entity;
    }

    private OutboxEventEntity.OutboxStatus toEntityStatus(OutboxEventRecord.OutboxStatus status) {
        if (status == null) {
            return null;
        }
        return OutboxEventEntity.OutboxStatus.valueOf(status.name());
    }

    private OutboxEventRecord.OutboxStatus toRecordStatus(OutboxEventEntity.OutboxStatus status) {
        if (status == null) {
            return null;
        }
        return OutboxEventRecord.OutboxStatus.valueOf(status.name());
    }
}
