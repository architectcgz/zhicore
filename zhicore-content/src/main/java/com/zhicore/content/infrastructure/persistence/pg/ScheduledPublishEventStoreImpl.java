package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.ScheduledPublishEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定时发布事件存储实现（R1）。
 */
@Repository
@RequiredArgsConstructor
public class ScheduledPublishEventStoreImpl implements ScheduledPublishEventStore {

    private final ScheduledPublishEventMapper mapper;

    @Override
    public LocalDateTime dbNow() {
        return mapper.selectDbNow();
    }

    @Override
    public void save(ScheduledPublishEventRecord eventRecord) {
        mapper.insert(toEntity(eventRecord));
    }

    @Override
    public Optional<ScheduledPublishEventRecord> findByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<ScheduledPublishEventEntity>()
                                .eq(ScheduledPublishEventEntity::getEventId, eventId)
                                .last("LIMIT 1")
                )
        ).map(this::toRecord);
    }

    @Override
    public void update(ScheduledPublishEventRecord eventRecord) {
        mapper.updateById(toEntity(eventRecord));
    }

    @Override
    public List<ScheduledPublishEventRecord> findDueScheduledPending(LocalDateTime dbNow,
                                                                     LocalDateTime cooldownBefore,
                                                                     int limit) {
        return mapper.findDueScheduledPending(dbNow, cooldownBefore, limit).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<ScheduledPublishEventRecord> findStaleScheduledPending(LocalDateTime dbNow,
                                                                       LocalDateTime staleBefore,
                                                                       int limit) {
        return mapper.findStaleScheduledPending(dbNow, staleBefore, limit).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public int casUpdateLastEnqueueAt(ScheduledPublishEventRecord eventRecord, LocalDateTime dbNow, String newEventId) {
        if (eventRecord == null || eventRecord.getId() == null) {
            return 0;
        }
        if (eventRecord.getLastEnqueueAt() == null) {
            return mapper.casUpdateLastEnqueueAtWhenNull(eventRecord.getId(), eventRecord.getScheduledAt(), dbNow, newEventId);
        }
        return mapper.casUpdateLastEnqueueAt(
                eventRecord.getId(),
                eventRecord.getScheduledAt(),
                eventRecord.getLastEnqueueAt(),
                dbNow,
                newEventId
        );
    }

    @Override
    public Optional<ScheduledPublishEventRecord> findActiveByPostId(Long postId) {
        if (postId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                mapper.selectOne(
                        new LambdaQueryWrapper<ScheduledPublishEventEntity>()
                                .eq(ScheduledPublishEventEntity::getPostId, postId)
                                .in(
                                        ScheduledPublishEventEntity::getStatus,
                                        ScheduledPublishEventEntity.ScheduledPublishStatus.PENDING,
                                        ScheduledPublishEventEntity.ScheduledPublishStatus.SCHEDULED_PENDING
                                )
                                .orderByDesc(ScheduledPublishEventEntity::getUpdatedAt)
                                .last("LIMIT 1")
                )
        ).map(this::toRecord);
    }

    private ScheduledPublishEventRecord toRecord(ScheduledPublishEventEntity entity) {
        return ScheduledPublishEventRecord.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .postId(entity.getPostId())
                .scheduledAt(entity.getScheduledAt())
                .status(entity.getStatus() == null
                        ? null
                        : ScheduledPublishEventRecord.ScheduledPublishStatus.valueOf(entity.getStatus().name()))
                .rescheduleRetryCount(entity.getRescheduleRetryCount())
                .publishRetryCount(entity.getPublishRetryCount())
                .lastEnqueueAt(entity.getLastEnqueueAt())
                .lastError(entity.getLastError())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ScheduledPublishEventEntity toEntity(ScheduledPublishEventRecord record) {
        ScheduledPublishEventEntity entity = new ScheduledPublishEventEntity();
        entity.setId(record.getId());
        entity.setEventId(record.getEventId());
        entity.setPostId(record.getPostId());
        entity.setScheduledAt(record.getScheduledAt());
        entity.setStatus(record.getStatus() == null
                ? null
                : ScheduledPublishEventEntity.ScheduledPublishStatus.valueOf(record.getStatus().name()));
        entity.setRescheduleRetryCount(record.getRescheduleRetryCount());
        entity.setPublishRetryCount(record.getPublishRetryCount());
        entity.setLastEnqueueAt(record.getLastEnqueueAt());
        entity.setLastError(record.getLastError());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setUpdatedAt(record.getUpdatedAt());
        return entity;
    }
}
