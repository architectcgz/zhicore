package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostContentUpdatedEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.event.PostMetadataUpdatedEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.event.PostRestoredEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.InternalEventTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 将内容服务内部事件持久化为可重放任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentInternalEventPublisher implements EventPublisher {

    private final InternalEventTaskMapper internalEventTaskMapper;
    private final ObjectMapper objectMapper;
    private final TransactionCommitSignal transactionCommitSignal;
    private final InternalEventTaskDispatcher internalEventTaskDispatcher;

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        if (persist(event)) {
            transactionCommitSignal.afterCommit(internalEventTaskDispatcher::signal);
        }
    }

    @Override
    @Transactional
    public void publishBatch(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        boolean persisted = false;
        for (DomainEvent event : events) {
            persisted = persist(event) || persisted;
        }
        if (persisted) {
            transactionCommitSignal.afterCommit(internalEventTaskDispatcher::signal);
        }
    }

    private boolean persist(DomainEvent event) {
        if (event == null) {
            return false;
        }

        try {
            InternalEventTaskEntity entity = new InternalEventTaskEntity();
            entity.setEventId(event.getEventId());
            entity.setEventType(event.getClass().getName());
            entity.setAggregateId(resolveAggregateId(event));
            entity.setAggregateVersion(event.getAggregateVersion());
            entity.setSchemaVersion(event.getSchemaVersion());
            entity.setPayload(objectMapper.writeValueAsString(event));
            entity.setPriority(resolvePriority(event));
            entity.setOccurredAt(event.getOccurredAt());
            Instant now = Instant.now();
            entity.setNextAttemptAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setRetryCount(0);
            entity.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.PENDING);
            internalEventTaskMapper.insert(entity);
            return true;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize internal event: eventId={}, eventType={}",
                    event.getEventId(), event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize internal event", e);
        }
    }

    private int resolvePriority(DomainEvent event) {
        if (event instanceof PostDeletedEvent
                || event instanceof PostPurgedEvent
                || event instanceof PostRestoredEvent
                || event instanceof PostPublishedDomainEvent) {
            return InternalEventTaskEntity.PRIORITY_HIGH;
        }
        if (event instanceof PostCreatedDomainEvent
                || event instanceof PostContentUpdatedEvent
                || event instanceof PostMetadataUpdatedEvent
                || event instanceof PostTagsUpdatedDomainEvent) {
            return InternalEventTaskEntity.PRIORITY_NORMAL;
        }
        return InternalEventTaskEntity.PRIORITY_NORMAL;
    }

    private Long resolveAggregateId(DomainEvent event) {
        Object aggregateId = event.getAggregateId();
        if (aggregateId instanceof PostId postId) {
            return postId.getValue();
        }
        if (aggregateId instanceof Long value) {
            return value;
        }
        return null;
    }
}
