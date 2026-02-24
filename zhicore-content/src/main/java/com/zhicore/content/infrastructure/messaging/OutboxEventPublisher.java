package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件发布器
 * 
 * 实现 Outbox 模式：
 * 1. 事务内写入 Outbox 表
 * 2. 不直接发送 MQ（避免双写不一致）
 * 3. 事务后由 OutboxEventDispatcher 异步投递到 RocketMQ
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements IntegrationEventPublisher {
    
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public void publish(IntegrationEvent event) {
        try {
            // 序列化事件为 JSON
            String payload = objectMapper.writeValueAsString(event);
            
            // 创建 Outbox 实体
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setEventId(event.getEventId());
            entity.setEventType(event.getClass().getName());
            entity.setAggregateId(event.getAggregateId());
            entity.setAggregateVersion(event.getAggregateVersion());
            entity.setSchemaVersion(event.getSchemaVersion());
            entity.setPayload(payload);
            entity.setOccurredAt(event.getOccurredAt());
            entity.setCreatedAt(Instant.now());
            entity.setRetryCount(0);
            entity.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
            
            // 事务内写入 Outbox 表
            outboxEventMapper.insert(entity);
            
            log.info("Integration event saved to outbox: eventId={}, eventType={}, aggregateId={}", 
                event.getEventId(), event.getClass().getSimpleName(), event.getAggregateId());
                
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize integration event: eventId={}", event.getEventId(), e);
            // 抛出异常，触发事务回滚
            throw new RuntimeException("Failed to serialize integration event", e);
        }
    }
    
    @Override
    @Transactional
    public void publishBatch(List<IntegrationEvent> events) {
        for (IntegrationEvent event : events) {
            publish(event);
        }
    }
}
