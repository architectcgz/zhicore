package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.dispatcher.ClaimBasedDispatcher;
import com.zhicore.content.application.model.OutboxEventTypes;
import com.zhicore.content.application.service.command.ScheduledPublishDelayLevelResolver;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.OutboxProperties;
import com.zhicore.content.infrastructure.config.RocketMqProperties;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.monitoring.ScheduledPublishTriggerDispatchMetrics;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.integration.messaging.DelayableIntegrationEvent;
import com.zhicore.integration.messaging.IntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件投递器。
 *
 * <p>重构后采用与内部事件一致的 claim-based 派发模型：
 * - 事务提交后主动唤醒
 * - 多 worker 并发 claim，不再依赖单实例全局锁
 * - PROCESSING 超时后自动回收
 * - 失败事件按 nextAttemptAt 退避重试
 */
@Slf4j
@Component
public class OutboxEventDispatcher extends ClaimBasedDispatcher<OutboxEventEntity> {

    /** RocketMQ 内置 DLQ topic 前缀：%DLQ%{consumerGroup} */
    private static final String ROCKETMQ_DLQ_PREFIX = "%DLQ%";

    private final OutboxEventMapper outboxEventMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;
    private final RocketMqProperties rocketMqProperties;
    private final ScheduledPublishProperties scheduledPublishProperties;
    private final ScheduledPublishTriggerDispatchMetrics scheduledPublishTriggerDispatchMetrics;
    private final AlertService alertService;

    public OutboxEventDispatcher(OutboxEventMapper outboxEventMapper,
                                 ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                                 ObjectMapper objectMapper,
                                 OutboxProperties outboxProperties,
                                 RocketMqProperties rocketMqProperties,
                                 ScheduledPublishProperties scheduledPublishProperties,
                                 ScheduledPublishTriggerDispatchMetrics scheduledPublishTriggerDispatchMetrics,
                                 AlertService alertService,
                                 @Qualifier("asyncEventExecutor") TaskExecutor taskExecutor,
                                 TransactionOperations transactionOperations) {
        super(
                "outbox",
                outboxProperties.getWorkerCount(),
                Duration.ofSeconds(outboxProperties.getClaimTimeoutSeconds()),
                taskExecutor,
                transactionOperations
        );
        this.outboxEventMapper = outboxEventMapper;
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.outboxProperties = outboxProperties;
        this.rocketMqProperties = rocketMqProperties;
        this.scheduledPublishProperties = scheduledPublishProperties;
        this.scheduledPublishTriggerDispatchMetrics = scheduledPublishTriggerDispatchMetrics;
        this.alertService = alertService;
    }

    /**
     * 服务重启后主动唤醒一次 outbox 派发器，确保停机期间积压的 PENDING/FAILED 事件能立即恢复投递。
     * 仅依赖固定延迟补扫时，历史 backlog 在部分运行环境下可能长期得不到首次 signal。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void triggerReplayOnStartup() {
        if (rocketMQTemplate == null) {
            log.warn("Outbox dispatcher startup replay skipped: RocketMQTemplate not available");
            return;
        }
        log.info("Outbox dispatcher startup replay triggered");
        signal();
    }

    @Override
    protected long sweepIntervalMillis() {
        return outboxProperties.getScanInterval();
    }

    @Override
    protected int batchSize() {
        return outboxProperties.getBatchSize();
    }

    @Override
    protected List<OutboxEventEntity> claimBatch(String workerId, int limit, Duration claimTimeout) {
        Instant now = Instant.now();
        Instant reclaimBefore = now.minus(claimTimeout);
        return outboxEventMapper.claimDispatchable(
                now,
                reclaimBefore,
                workerId,
                limit,
                outboxProperties.getMaxRetry()
        );
    }

    @Override
    protected void handleClaimed(OutboxEventEntity entity) {
        try {
            if (rocketMQTemplate == null) {
                throw new IllegalStateException("RocketMQTemplate not available");
            }
            if (OutboxEventTypes.SCHEDULED_PUBLISH_DLQ.equals(entity.getEventType())) {
                dispatchScheduledPublishDlq(entity);
                return;
            }

            Class<?> eventClass = Class.forName(entity.getEventType());
            IntegrationEvent event = (IntegrationEvent) objectMapper.readValue(entity.getPayload(), eventClass);

            String destination = rocketMqProperties.getPostEvents() + ":" + event.getTag();
            Message<String> message = buildMessage(entity, event);
            dispatchEvent(destination, message, event);

            markDispatched(entity, Instant.now());

            log.info("Outbox event dispatched: eventId={}, eventType={}, aggregateId={}",
                    entity.getEventId(), event.getClass().getSimpleName(), entity.getAggregateId());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to dispatch outbox event: eventId=" + entity.getEventId(), ex);
        }
    }

    @Override
    protected void handleFailure(OutboxEventEntity entity, Exception exception) {
        Instant now = Instant.now();
        int currentRetry = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
        int nextRetryCount = currentRetry + 1;

        entity.setRetryCount(nextRetryCount);
        entity.setLastError(exception.getMessage());
        entity.setUpdatedAt(now);
        entity.setClaimedAt(null);
        entity.setClaimedBy(null);

        if (nextRetryCount >= outboxProperties.getMaxRetry()) {
            entity.setStatus(OutboxEventEntity.OutboxStatus.DEAD);
            entity.setNextAttemptAt(now);

            log.error("Outbox event dead after {} retries: eventId={}, eventType={}, error={}",
                    outboxProperties.getMaxRetry(), entity.getEventId(), entity.getEventType(), exception.getMessage(), exception);

            alertService.alertOutboxDispatchFailed(
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getAggregateId(),
                    entity.getRetryCount(),
                    exception.getMessage()
            );
        } else {
            entity.setStatus(OutboxEventEntity.OutboxStatus.FAILED);
            entity.setNextAttemptAt(now.plusSeconds(backoffSeconds(nextRetryCount)));
            log.warn("Outbox event dispatch failed (retry {}/{}): eventId={}, eventType={}, error={}",
                    entity.getRetryCount(), outboxProperties.getMaxRetry(), entity.getEventId(),
                    entity.getEventType(), exception.getMessage());
        }

        outboxEventMapper.updateById(entity);
    }

    protected void dispatchScheduledPublishDlq(OutboxEventEntity entity) {
        String dlqTopic = ROCKETMQ_DLQ_PREFIX + rocketMqProperties.getPostScheduleConsumerGroup();
        String destination = dlqTopic + ":" + rocketMqProperties.getScheduledPublishDlqTag();

        Message<String> message = MessageBuilder
                .withPayload(entity.getPayload())
                .setHeader("eventId", entity.getEventId())
                .setHeader("eventType", entity.getEventType())
                .setHeader("aggregateId", entity.getAggregateId())
                .setHeader("aggregateVersion", entity.getAggregateVersion())
                .setHeader("schemaVersion", entity.getSchemaVersion())
                .build();

        rocketMQTemplate.syncSend(destination, message);
        markDispatched(entity, Instant.now());

        log.warn("Scheduled publish DLQ event dispatched: eventId={}, aggregateId={}, destination={}",
                entity.getEventId(), entity.getAggregateId(), destination);
    }

    private void markDispatched(OutboxEventEntity entity, Instant now) {
        entity.setStatus(OutboxEventEntity.OutboxStatus.SUCCEEDED);
        entity.setDispatchedAt(now);
        entity.setUpdatedAt(now);
        entity.setClaimedAt(null);
        entity.setClaimedBy(null);
        outboxEventMapper.updateById(entity);
    }

    private long backoffSeconds(int retryCount) {
        long value = 1L << Math.min(retryCount - 1, 20);
        return Math.min(value, outboxProperties.getMaxBackoffSeconds());
    }

    private Message<String> buildMessage(OutboxEventEntity entity, IntegrationEvent event) {
        return MessageBuilder
                .withPayload(entity.getPayload())
                .setHeader("eventId", event.getEventId())
                .setHeader("eventType", event.getClass().getSimpleName())
                .setHeader("aggregateId", event.getAggregateId())
                .setHeader("aggregateVersion", event.getAggregateVersion())
                .setHeader("schemaVersion", event.getSchemaVersion())
                .build();
    }

    private void dispatchEvent(String destination, Message<String> message, IntegrationEvent event) {
        if (event instanceof PostScheduleExecuteIntegrationEvent scheduledPublishEvent) {
            dispatchScheduledPublishTrigger(destination, message, scheduledPublishEvent);
            return;
        }

        if (event instanceof DelayableIntegrationEvent delayable
                && delayable.getDelayLevel() != null
                && delayable.getDelayLevel() > 0) {
            rocketMQTemplate.syncSend(destination, message, 3000, delayable.getDelayLevel());
            return;
        }

        rocketMQTemplate.syncSend(destination, message);
    }

    /**
     * 定时发布优先使用 timer message，保证触发时间基于绝对 scheduledAt，
     * 避免 outbox 派发抖动把旧的 delayLevel 再额外往后推一轮。
     */
    private void dispatchScheduledPublishTrigger(String destination,
                                                 Message<String> message,
                                                 PostScheduleExecuteIntegrationEvent event) {
        Instant scheduledAt = event.getScheduledAt();
        if (scheduledAt == null) {
            dispatchScheduledPublishWithFallback(destination, message, event, "missing_scheduled_at", null);
            return;
        }

        long nowMillis = Instant.now().toEpochMilli();
        long deliverAtMillis = scheduledAt.toEpochMilli();
        if (deliverAtMillis <= nowMillis) {
            rocketMQTemplate.syncSend(destination, message);
            scheduledPublishTriggerDispatchMetrics.recordImmediateDispatch("already_due");
            return;
        }

        if (!scheduledPublishProperties.isTimerMessageEnabled()) {
            dispatchScheduledPublishWithFallback(destination, message, event, "timer_disabled", null);
            return;
        }

        try {
            rocketMQTemplate.syncSendDeliverTimeMills(destination, message, deliverAtMillis);
            scheduledPublishTriggerDispatchMetrics.recordTimerDispatch();
        } catch (RuntimeException ex) {
            dispatchScheduledPublishWithFallback(destination, message, event, "timer_send_failed", ex);
        }
    }

    private void dispatchScheduledPublishWithFallback(String destination,
                                                      Message<String> message,
                                                      PostScheduleExecuteIntegrationEvent event,
                                                      String reason,
                                                      RuntimeException timerException) {
        int delayLevel = resolveDelayLevel(event);
        if (delayLevel > 0) {
            if (timerException != null) {
                log.warn("Timer message dispatch failed, fallback to delay level: eventId={}, taskId={}, scheduledAt={}, delayLevel={}, error={}",
                        event.getEventId(),
                        event.getScheduledPublishEventId(),
                        event.getScheduledAt(),
                        delayLevel,
                        timerException.getMessage());
            }
            rocketMQTemplate.syncSend(destination, message, 3000, delayLevel);
            scheduledPublishTriggerDispatchMetrics.recordDelayLevelDispatch(reason);
            if (timerException != null || "timer_disabled".equals(reason)) {
                scheduledPublishTriggerDispatchMetrics.recordTimerFallback(reason, "delay_level");
            }
            return;
        }

        if (timerException != null) {
            log.warn("Timer message dispatch failed, fallback to immediate send: eventId={}, taskId={}, scheduledAt={}, error={}",
                    event.getEventId(),
                    event.getScheduledPublishEventId(),
                    event.getScheduledAt(),
                    timerException.getMessage());
        }
        rocketMQTemplate.syncSend(destination, message);
        scheduledPublishTriggerDispatchMetrics.recordImmediateDispatch(reason);
        if (timerException != null || "timer_disabled".equals(reason)) {
            scheduledPublishTriggerDispatchMetrics.recordTimerFallback(reason, "immediate");
        }
    }

    private int resolveDelayLevel(PostScheduleExecuteIntegrationEvent event) {
        Instant scheduledAt = event.getScheduledAt();
        if (scheduledAt != null) {
            long remainingMillis = ChronoUnit.MILLIS.between(Instant.now(), scheduledAt);
            if (remainingMillis > 0) {
                long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
                return ScheduledPublishDelayLevelResolver.resolve(remainingSeconds);
            }
            return 0;
        }

        Integer legacyDelayLevel = event.getDelayLevel();
        return legacyDelayLevel != null && legacyDelayLevel > 0 ? legacyDelayLevel : 0;
    }
}
