package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.OutboxProperties;
import com.zhicore.content.infrastructure.config.RocketMqProperties;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.monitoring.ScheduledPublishTriggerDispatchMetrics;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.integration.messaging.IntegrationEvent;
import com.zhicore.integration.messaging.post.AuthorInfoCompensationIntegrationEvent;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventDispatcher 测试")
class OutboxEventDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private AlertService alertService;

    @Test
    @DisplayName("claim 到可投递事件时应该发送 MQ 并标记为 SUCCEEDED")
    void shouldDispatchClaimedOutboxEvent() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        OutboxEventEntity entity = task(postPublishedEvent("evt-1"));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any()))
                .thenReturn(mock(SendResult.class));

        dispatcher.signal();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventMapper).updateById(captor.capture());
        assertEquals(OutboxEventEntity.OutboxStatus.SUCCEEDED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getAggregateVersion());
    }

    @Test
    @DisplayName("超过最大重试次数时应该标记为 DEAD 并告警")
    void shouldMarkDeadWhenRetryExhausted() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        properties.setMaxRetry(1);
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        OutboxEventEntity entity = task(postPublishedEvent("evt-2"));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("mq down"))
                .when(rocketMQTemplate)
                .syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any());

        dispatcher.signal();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventMapper).updateById(captor.capture());
        assertEquals(OutboxEventEntity.OutboxStatus.DEAD, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        assertNotNull(captor.getValue().getNextAttemptAt());
        verify(alertService).alertOutboxDispatchFailed(
                anyString(), anyString(), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("仍有剩余重试次数时应该标记为 FAILED")
    void shouldMarkFailedWhenRetryRemaining() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        properties.setMaxRetry(2);
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        OutboxEventEntity entity = task(postPublishedEvent("evt-2b"));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("mq down"))
                .when(rocketMQTemplate)
                .syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any());

        dispatcher.signal();

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventMapper).updateById(captor.capture());
        assertEquals(OutboxEventEntity.OutboxStatus.FAILED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        assertNotNull(captor.getValue().getNextAttemptAt());
    }

    @Test
    @DisplayName("定时发布事件在开启 timer message 时应该按 scheduledAt 发送")
    void shouldDispatchScheduledPublishWithTimerMessage() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        Instant scheduledAt = Instant.now().plusSeconds(10);
        OutboxEventEntity entity = task(postScheduleExecuteEvent("evt-3", scheduledAt, 3));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSendDeliverTimeMills(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any(), anyLong()))
                .thenReturn(mock(SendResult.class));

        dispatcher.signal();

        verify(rocketMQTemplate).syncSendDeliverTimeMills(
                anyString(),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq(scheduledAt.toEpochMilli())
        );
        assertEquals(1.0, meterRegistry.get(ScheduledPublishTriggerDispatchMetrics.DISPATCH_METRIC_NAME)
                .tags("queue", ScheduledPublishTriggerDispatchMetrics.QUEUE_NAME, "mode", "timer", "reason", "scheduled_at")
                .counter()
                .count());
    }

    @Test
    @DisplayName("定时发布事件在 broker 不支持 timer message 时应该回退到 delayLevel")
    void shouldFallbackToDelayLevelWhenTimerMessageFails() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        Instant scheduledAt = Instant.now().plusSeconds(10);
        OutboxEventEntity entity = task(postScheduleExecuteEvent("evt-3b", scheduledAt, 3));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("timer not supported"))
                .when(rocketMQTemplate)
                .syncSendDeliverTimeMills(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any(), anyLong());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any(), anyLong(), anyInt()))
                .thenReturn(mock(SendResult.class));

        dispatcher.signal();

        verify(rocketMQTemplate).syncSend(
                anyString(),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq(3000L),
                eq(3)
        );
        assertEquals(1.0, meterRegistry.get(ScheduledPublishTriggerDispatchMetrics.DISPATCH_METRIC_NAME)
                .tags("queue", ScheduledPublishTriggerDispatchMetrics.QUEUE_NAME, "mode", "delay_level", "reason", "timer_send_failed")
                .counter()
                .count());
        assertEquals(1.0, meterRegistry.get(ScheduledPublishTriggerDispatchMetrics.FALLBACK_METRIC_NAME)
                .tags("queue", ScheduledPublishTriggerDispatchMetrics.QUEUE_NAME, "source", "timer", "target", "delay_level", "reason", "timer_send_failed")
                .counter()
                .count());
    }

    @Test
    @DisplayName("关闭 timer message 后定时发布事件应该直接走 delayLevel 回退路径")
    void shouldUseDelayLevelWhenTimerMessageDisabled() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        scheduledPublishProperties.setTimerMessageEnabled(false);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        Instant scheduledAt = Instant.now().plusSeconds(10);
        OutboxEventEntity entity = task(postScheduleExecuteEvent("evt-3c", scheduledAt, 3));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any(), anyLong(), anyInt()))
                .thenReturn(mock(SendResult.class));

        dispatcher.signal();

        verify(rocketMQTemplate).syncSend(
                anyString(),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq(3000L),
                eq(3)
        );
        assertEquals(1.0, meterRegistry.get(ScheduledPublishTriggerDispatchMetrics.DISPATCH_METRIC_NAME)
                .tags("queue", ScheduledPublishTriggerDispatchMetrics.QUEUE_NAME, "mode", "delay_level", "reason", "timer_disabled")
                .counter()
                .count());
        assertEquals(1.0, meterRegistry.get(ScheduledPublishTriggerDispatchMetrics.FALLBACK_METRIC_NAME)
                .tags("queue", ScheduledPublishTriggerDispatchMetrics.QUEUE_NAME, "source", "timer", "target", "delay_level", "reason", "timer_disabled")
                .counter()
                .count());
    }

    @Test
    @DisplayName("非定时发布的延迟事件仍应使用 delayLevel 发送")
    void shouldDispatchGenericDelayableOutboxEventWithDelayLevel() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        OutboxEventEntity entity = task(authorInfoCompensationEvent("evt-4", 5));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any(), anyLong(), anyInt()))
                .thenReturn(mock(SendResult.class));

        dispatcher.signal();

        verify(rocketMQTemplate).syncSend(
                anyString(),
                org.mockito.ArgumentMatchers.<Message<?>>any(),
                eq(3000L),
                eq(5)
        );
    }

    @Test
    @DisplayName("到期的定时发布事件应该立即发送并记录 immediate 指标")
    void shouldDispatchImmediateWhenScheduledPublishAlreadyDue() throws Exception {
        OutboxProperties properties = new OutboxProperties();
        RocketMqProperties rocketMqProperties = new RocketMqProperties();
        ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        TaskExecutor taskExecutor = Runnable::run;
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
                outboxEventMapper,
                rocketMqTemplateProvider(),
                objectMapper,
                properties,
                rocketMqProperties,
                scheduledPublishProperties,
                new ScheduledPublishTriggerDispatchMetrics(meterRegistry),
                alertService,
                taskExecutor,
                immediateTransactions()
        );

        Instant scheduledAt = Instant.now().minusSeconds(5);
        OutboxEventEntity entity = task(postScheduleExecuteEvent("evt-due", scheduledAt, 0));
        when(outboxEventMapper.claimDispatchable(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any()))
                .thenReturn(mock(SendResult.class));

        dispatcher.signal();

        verify(rocketMQTemplate).syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any());
        assertEquals(1.0, meterRegistry.get(ScheduledPublishTriggerDispatchMetrics.DISPATCH_METRIC_NAME)
                .tags("queue", ScheduledPublishTriggerDispatchMetrics.QUEUE_NAME, "mode", "immediate", "reason", "already_due")
                .counter()
                .count());
    }

    private OutboxEventEntity task(IntegrationEvent event) throws Exception {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(1L);
        entity.setEventId(event.getEventId());
        entity.setEventType(event.getClass().getName());
        entity.setAggregateId(event.getAggregateId());
        entity.setAggregateVersion(event.getAggregateVersion());
        entity.setSchemaVersion(event.getSchemaVersion());
        entity.setPayload(objectMapper.writeValueAsString(event));
        entity.setOccurredAt(event.getOccurredAt());
        entity.setNextAttemptAt(Instant.now());
        entity.setRetryCount(0);
        entity.setStatus(OutboxEventEntity.OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private PostPublishedIntegrationEvent postPublishedEvent(String eventId) {
        return new PostPublishedIntegrationEvent(
                eventId,
                Instant.parse("2026-03-14T10:00:00Z"),
                1L,
                2L,
                "title",
                "excerpt",
                Instant.parse("2026-03-14T10:00:00Z"),
                1L
        );
    }

    private PostScheduleExecuteIntegrationEvent postScheduleExecuteEvent(String eventId, Instant scheduledAt, Integer delayLevel) {
        return new PostScheduleExecuteIntegrationEvent(
                eventId,
                Instant.now(),
                1L,
                1L,
                2L,
                scheduledAt,
                delayLevel,
                "task-" + eventId
        );
    }

    private AuthorInfoCompensationIntegrationEvent authorInfoCompensationEvent(String eventId, Integer delayLevel) {
        return new AuthorInfoCompensationIntegrationEvent(
                eventId,
                Instant.now(),
                1L,
                1L,
                2L,
                delayLevel
        );
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }

    private ObjectProvider<RocketMQTemplate> rocketMqTemplateProvider() {
        return new StaticListableBeanFactory(Map.of("rocketMQTemplate", rocketMQTemplate))
                .getBeanProvider(RocketMQTemplate.class);
    }
}
