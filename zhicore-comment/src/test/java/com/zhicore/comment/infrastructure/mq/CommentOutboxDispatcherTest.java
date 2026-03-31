package com.zhicore.comment.infrastructure.mq;

import com.zhicore.comment.domain.model.OutboxEvent;
import com.zhicore.comment.domain.model.OutboxEventStatus;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import com.zhicore.comment.infrastructure.config.CommentOutboxProperties;
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

import java.time.OffsetDateTime;
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
@DisplayName("CommentOutboxDispatcher 测试")
class CommentOutboxDispatcherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Test
    @DisplayName("claim 到评论 outbox 事件后应该发送并标记为 SUCCEEDED")
    void shouldDispatchClaimedCommentOutboxEvent() {
        CommentOutboxProperties properties = new CommentOutboxProperties();
        CommentOutboxDispatcher dispatcher = new CommentOutboxDispatcher(
                outboxEventRepository,
                rocketMQTemplate,
                properties,
                directExecutor(),
                immediateTransactions()
        );

        OutboxEvent event = event("evt-1", "2001", OutboxEventStatus.PENDING);
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
    @DisplayName("发送失败且超过重试上限时应该标记为 DEAD")
    void shouldMarkCommentOutboxEventDead() {
        CommentOutboxProperties properties = new CommentOutboxProperties();
        CommentOutboxDispatcher dispatcher = new CommentOutboxDispatcher(
                outboxEventRepository,
                rocketMQTemplate,
                properties,
                directExecutor(),
                immediateTransactions()
        );

        OutboxEvent event = event("evt-2", "2001", OutboxEventStatus.PENDING);
        event.setMaxRetries(1);
        when(outboxEventRepository.claimRetryableEvents(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(event))
                .thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("mq down"))
                .when(rocketMQTemplate)
                .syncSend(anyString(), org.mockito.ArgumentMatchers.<Message<?>>any());

        dispatcher.dispatch();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).update(captor.capture());
        assertEquals(OutboxEventStatus.DEAD, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        assertNull(captor.getValue().getClaimedAt());
        assertNull(captor.getValue().getClaimedBy());
    }

    private OutboxEvent event(String id, String shardingKey, OutboxEventStatus status) {
        OutboxEvent event = OutboxEvent.of("topic-a", "tag-a", shardingKey, "{\"event\":\"x\"}");
        event.setId(id);
        event.setStatus(status);
        event.setClaimedBy("worker-1");
        event.setClaimedAt(OffsetDateTime.now().minusMinutes(5));
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
