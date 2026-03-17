package com.zhicore.user.infrastructure.mq;

import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.model.OutboxEventStatus;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.infrastructure.config.UserOutboxProperties;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserOutboxDispatcher 测试")
class UserOutboxDispatcherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Test
    @DisplayName("claim 到用户 outbox 事件后应该发送并标记为 SUCCEEDED")
    void shouldDispatchClaimedUserOutboxEvent() {
        UserOutboxProperties properties = new UserOutboxProperties();
        UserOutboxDispatcher dispatcher = new UserOutboxDispatcher(
                outboxEventRepository,
                rocketMQTemplate,
                properties,
                directExecutor(),
                immediateTransactions()
        );

        OutboxEvent event = event("evt-1", "1001", OutboxEventStatus.PENDING);
        when(outboxEventRepository.claimRetryableEvents(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(event))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any()))
                .thenReturn(mock(SendResult.class));

        dispatcher.dispatch();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).update(captor.capture());
        assertEquals(OutboxEventStatus.SUCCEEDED, captor.getValue().getStatus());
        assertNull(captor.getValue().getClaimedAt());
        assertNull(captor.getValue().getClaimedBy());
    }

    @Test
    @DisplayName("发送失败时应该增加重试次数并切换到 FAILED")
    void shouldMarkUserOutboxEventFailed() {
        UserOutboxProperties properties = new UserOutboxProperties();
        UserOutboxDispatcher dispatcher = new UserOutboxDispatcher(
                outboxEventRepository,
                rocketMQTemplate,
                properties,
                directExecutor(),
                immediateTransactions()
        );

        OutboxEvent event = event("evt-2", "1001", OutboxEventStatus.PENDING);
        when(outboxEventRepository.claimRetryableEvents(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(event))
                .thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("mq down"))
                .when(rocketMQTemplate)
                .syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any());

        dispatcher.dispatch();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).update(captor.capture());
        assertEquals(OutboxEventStatus.FAILED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        assertNull(captor.getValue().getClaimedAt());
        assertNull(captor.getValue().getClaimedBy());
    }

    @Test
    @DisplayName("claim 到超时回收的 PROCESSING 事件后应该继续处理")
    void shouldDispatchReclaimedUserOutboxEvent() {
        UserOutboxProperties properties = new UserOutboxProperties();
        UserOutboxDispatcher dispatcher = new UserOutboxDispatcher(
                outboxEventRepository,
                rocketMQTemplate,
                properties,
                directExecutor(),
                immediateTransactions()
        );

        OutboxEvent event = event("evt-3", "1001", OutboxEventStatus.PROCESSING);
        when(outboxEventRepository.claimRetryableEvents(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(event))
                .thenReturn(List.of());
        when(rocketMQTemplate.syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any()))
                .thenReturn(mock(SendResult.class));

        dispatcher.dispatch();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).update(captor.capture());
        assertEquals(OutboxEventStatus.SUCCEEDED, captor.getValue().getStatus());
        assertNull(captor.getValue().getClaimedAt());
        assertNull(captor.getValue().getClaimedBy());
    }

    private OutboxEvent event(String id, String shardingKey, OutboxEventStatus status) {
        OutboxEvent event = OutboxEvent.of("topic-a", "tag-a", shardingKey, "{\"event\":\"x\"}");
        event.setId(id);
        event.setStatus(status);
        event.setClaimedBy("worker-1");
        event.setClaimedAt(LocalDateTime.now().minusMinutes(5));
        return event;
    }

    private TaskExecutor directExecutor() {
        return Runnable::run;
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(mock(TransactionStatus.class));
            }
        };
    }
}
