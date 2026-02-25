package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.port.cache.LockManager;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.cache.LockKeys;
import com.zhicore.content.infrastructure.config.OutboxProperties;
import com.zhicore.content.infrastructure.config.RocketMqProperties;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.integration.messaging.DelayableIntegrationEvent;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件投递器
 * 
 * 后台线程定期扫描 Outbox 表，投递待发送的事件到 RocketMQ
 * 
 * 特性：
 * 1. 定期扫描（默认每5秒，支持 Nacos 动态刷新）
 * 2. 批量读取（默认100条）
 * 3. 重试机制（最多3次）
 * 4. 失败告警（超过最大重试次数）
 * 5. 支持动态配置（通过 Nacos 刷新）
 * 6. 分布式锁保护（多实例部署时防止重复投递）
 * 7. 看门狗自动续期（防止批量投递时锁过期）
 * 
 * 注意：使用 SchedulingConfigurer 实现动态调整扫描间隔
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class OutboxEventDispatcher implements SchedulingConfigurer {
    
    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;  // 不等待，获取不到直接返回

    /**
     * RocketMQ 内置 DLQ topic 前缀：%DLQ%{consumerGroup}
     *
     * <p>本项目的“定时发布执行”消费者组为 post-schedule-consumer-group，
     * 定时发布失败会以 DLQ 事件形式写入 Outbox，并由投递器发送到该 DLQ topic。
     */
    private static final String ROCKETMQ_DLQ_PREFIX = "%DLQ%";
    private static final String POST_SCHEDULE_CONSUMER_GROUP = "post-schedule-consumer-group";
    private static final String TAG_SCHEDULED_PUBLISH_DLQ = "scheduled-publish-dlq";
    
    private final OutboxEventMapper outboxEventMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;
    private final RocketMqProperties rocketMqProperties;
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    private final AlertService alertService;
    
    /**
     * 配置定时任务
     * 
     * 使用 SchedulingConfigurer 动态读取配置，支持 Nacos 刷新
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
            this::dispatch,  // 执行的任务
            Duration.ofMillis(outboxProperties.getScanInterval())
        );
    }
    
    /**
     * 定期投递 Outbox 事件
     * 
     * 使用分布式锁确保多实例部署时只有一个实例执行投递任务
     * 使用看门狗自动续期，防止批量投递时锁过期
     */
    public void dispatch() {
        String lockKey = lockKeys.outboxDispatcher();
        
        // 尝试获取分布式锁（启用看门狗）
        boolean lockAcquired = lockManager.tryLockWithWatchdog(lockKey, LOCK_WAIT_TIME);
        
        if (!lockAcquired) {
            // 获取锁失败，说明其他实例正在执行，直接返回
            log.debug("无法获取 Outbox 投递锁，跳过本次执行");
            return;
        }
        
        try {
            // 批量读取待投递事件
            List<OutboxEventEntity> pendingEvents = outboxEventMapper.findByStatusOrderByCreatedAtAsc(
                OutboxEventEntity.OutboxStatus.PENDING.name(),
                outboxProperties.getBatchSize()
            );
            
            if (pendingEvents.isEmpty()) {
                return;
            }
            
            log.info("Found {} pending outbox events to dispatch", pendingEvents.size());
            
            // 逐个投递事件
            for (OutboxEventEntity entity : pendingEvents) {
                try {
                    dispatchEvent(entity);
                } catch (Exception e) {
                    handleDispatchFailure(entity, e);
                }
            }
        } finally {
            // 释放分布式锁
            try {
                lockManager.unlock(lockKey);
            } catch (Exception e) {
                log.error("释放 Outbox 投递锁失败", e);
            }
        }
    }
    
    /**
     * 投递单个事件
     * 
     * @param entity Outbox 事件实体
     * @throws Exception 投递失败时抛出异常
     */
    @Transactional
    protected void dispatchEvent(OutboxEventEntity entity) throws Exception {
        if (OutboxEventTypes.SCHEDULED_PUBLISH_DLQ.equals(entity.getEventType())) {
            dispatchScheduledPublishDlq(entity);
            return;
        }

        // 反序列化事件
        Class<?> eventClass = Class.forName(entity.getEventType());
        IntegrationEvent event = (IntegrationEvent) objectMapper.readValue(
            entity.getPayload(),
            eventClass
        );
        
        // 构建 RocketMQ 消息
        String destination = rocketMqProperties.getPostEvents() + ":" + event.getTag();
        Message<String> message = MessageBuilder
            .withPayload(entity.getPayload())
            .setHeader("eventId", event.getEventId())
            .setHeader("eventType", event.getClass().getSimpleName())
            .setHeader("aggregateId", event.getAggregateId())
            .setHeader("aggregateVersion", event.getAggregateVersion())
            .setHeader("schemaVersion", event.getSchemaVersion())
            .build();
        
        // 发送到 RocketMQ（支持延迟消息）
        if (event instanceof DelayableIntegrationEvent delayable &&
            delayable.getDelayLevel() != null &&
            delayable.getDelayLevel() > 0) {
            rocketMQTemplate.syncSend(destination, message, 3000, delayable.getDelayLevel());
        } else {
            rocketMQTemplate.syncSend(destination, message);
        }
        
        // 更新 Outbox 状态为已投递
        entity.setStatus(OutboxEventEntity.OutboxStatus.DISPATCHED);
        Instant now = Instant.now();
        entity.setDispatchedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.updateById(entity);
        
        log.info("Outbox event dispatched: eventId={}, eventType={}, aggregateId={}",
            entity.getEventId(), event.getClass().getSimpleName(), entity.getAggregateId());
    }

    @Transactional
    protected void dispatchScheduledPublishDlq(OutboxEventEntity entity) {
        String dlqTopic = ROCKETMQ_DLQ_PREFIX + POST_SCHEDULE_CONSUMER_GROUP;
        String destination = dlqTopic + ":" + TAG_SCHEDULED_PUBLISH_DLQ;

        Message<String> message = MessageBuilder
                .withPayload(entity.getPayload())
                .setHeader("eventId", entity.getEventId())
                .setHeader("eventType", entity.getEventType())
                .setHeader("aggregateId", entity.getAggregateId())
                .setHeader("aggregateVersion", entity.getAggregateVersion())
                .setHeader("schemaVersion", entity.getSchemaVersion())
                .build();

        rocketMQTemplate.syncSend(destination, message);

        entity.setStatus(OutboxEventEntity.OutboxStatus.DISPATCHED);
        Instant now = Instant.now();
        entity.setDispatchedAt(now);
        entity.setUpdatedAt(now);
        outboxEventMapper.updateById(entity);

        log.warn("Scheduled publish DLQ event dispatched: eventId={}, aggregateId={}, destination={}",
                entity.getEventId(), entity.getAggregateId(), destination);
    }
    
    /**
     * 处理投递失败
     * 
     * 实现重试机制：
     * 1. 重试次数 < 最大重试次数：增加重试计数，保持 PENDING 状态
     * 2. 重试次数 >= 最大重试次数：标记为 FAILED，发送告警
     * 
     * @param entity Outbox 事件实体
     * @param e 异常信息
     */
    @Transactional
    protected void handleDispatchFailure(OutboxEventEntity entity, Exception e) {
        Instant now = Instant.now();

        // 增加重试计数
        int currentRetry = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
        entity.setRetryCount(currentRetry + 1);
        entity.setLastError(e.getMessage());
        entity.setUpdatedAt(now);
        
        // 检查是否超过最大重试次数
        if (entity.getRetryCount() >= outboxProperties.getMaxRetry()) {
            // 标记为失败
            entity.setStatus(OutboxEventEntity.OutboxStatus.FAILED);
            
            log.error("Outbox event dispatch failed after {} retries: eventId={}, eventType={}, error={}",
                outboxProperties.getMaxRetry(), entity.getEventId(), entity.getEventType(), e.getMessage(), e);
            
            // 发送告警通知（限流：每 eventType 每分钟最多 10 条）
            alertService.alertOutboxDispatchFailed(
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getAggregateId(),
                    entity.getRetryCount(),
                    e.getMessage()
            );
        } else {
            // 保持 PENDING 状态，等待下次重试
            log.warn("Outbox event dispatch failed (retry {}/{}): eventId={}, eventType={}, error={}",
                entity.getRetryCount(), outboxProperties.getMaxRetry(), entity.getEventId(), 
                entity.getEventType(), e.getMessage());
        }
        
        // 更新实体
        outboxEventMapper.updateById(entity);
    }
}
