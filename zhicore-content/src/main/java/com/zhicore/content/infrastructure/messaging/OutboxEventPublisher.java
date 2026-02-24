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
 * 1) 业务事务内写入 outbox_event 表
 * 2) 不在事务内直接发送 MQ（避免双写不一致）
 * 3) 事务后由 {@code OutboxEventDispatcher} 异步投递到 RocketMQ
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
        // Outbox 强约束：aggregateVersion 不允许为空。
        // 语义：aggregateVersion 表示“业务事务提交时刻”聚合根的版本号，用于：
        // 1) 幂等与去重（同一聚合的事件顺序校验）
        // 2) 并发冲突诊断（定位乱序/重复投递）
        if (event.getAggregateVersion() == null) {
            throw new IllegalArgumentException("aggregateVersion 不能为空（Outbox 事件必须携带聚合版本号）");
        }

        try {
            // 序列化事件为 JSON
            String payload = objectMapper.writeValueAsString(event);
            
            // 构建 Outbox 记录（事务内落库）
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setEventId(event.getEventId());
            entity.setEventType(event.getClass().getName());
            entity.setAggregateId(event.getAggregateId());
            entity.setAggregateVersion(event.getAggregateVersion());
            entity.setSchemaVersion(event.getSchemaVersion());
            entity.setPayload(payload);
            entity.setOccurredAt(event.getOccurredAt());
            Instant now = Instant.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setRetryCount(0);
            entity.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
            
            // 事务内写入 Outbox 表（后续由调度器/派发器异步投递）
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
