package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.domain.event.DomainEvent;
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

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }

        try {
            InternalEventTaskEntity entity = new InternalEventTaskEntity();
            entity.setEventId(event.getEventId());
            entity.setEventType(event.getClass().getName());
            entity.setAggregateId(resolveAggregateId(event));
            entity.setAggregateVersion(event.getAggregateVersion());
            entity.setSchemaVersion(event.getSchemaVersion());
            entity.setPayload(objectMapper.writeValueAsString(event));
            entity.setOccurredAt(event.getOccurredAt());
            Instant now = Instant.now();
            entity.setNextAttemptAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setRetryCount(0);
            entity.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.PENDING);
            internalEventTaskMapper.insert(entity);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize internal event: eventId={}, eventType={}",
                    event.getEventId(), event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize internal event", e);
        }
    }

    @Override
    @Transactional
    public void publishBatch(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(this::publish);
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
