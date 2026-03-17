package com.zhicore.message.infrastructure.mq;

import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.application.service.ImMessageBridgeService;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.infrastructure.config.MessageOutboxProperties;
import com.zhicore.message.infrastructure.push.MessagePushDispatchService;
import com.zhicore.message.infrastructure.repository.mapper.MessageOutboxTaskMapper;
import com.zhicore.message.infrastructure.repository.po.MessageOutboxTaskPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageOutboxDispatcher 测试")
class MessageOutboxDispatcherTest {

    @Mock
    private MessageOutboxTaskMapper messageOutboxTaskMapper;

    @Mock
    private MessagePushDispatchService messagePushDispatchService;

    @Mock
    private ImMessageBridgeService imMessageBridgeService;

    @Test
    @DisplayName("claim 到任务后应该完成推送与 IM 同步")
    void shouldDispatchClaimedTasks() {
        MessageOutboxProperties properties = new MessageOutboxProperties();
        MessageOutboxDispatcher dispatcher = new MessageOutboxDispatcher(
                messageOutboxTaskMapper,
                properties,
                messagePushDispatchService,
                imMessageBridgeService,
                directExecutor(),
                immediateTransactions()
        );

        MessageSentPublishRequest sentRequest = MessageSentPublishRequest.from(
                Message.createText(1L, 2L, 3L, 4L, "hello")
        );
        MessageRecallSyncRequest recallRequest = new MessageRecallSyncRequest(1L, 2L, 3L);

        when(messageOutboxTaskMapper.claimDispatchable(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(
                        task(1L, 2L, MessageOutboxTaskStatus.PENDING, MessageOutboxTaskType.MESSAGE_PUSH, JsonUtils.toJson(sentRequest)),
                        task(2L, 2L, MessageOutboxTaskStatus.PENDING, MessageOutboxTaskType.MESSAGE_SENT_IM, JsonUtils.toJson(sentRequest)),
                        task(3L, 2L, MessageOutboxTaskStatus.PENDING, MessageOutboxTaskType.MESSAGE_RECALL_IM, JsonUtils.toJson(recallRequest))
                ))
                .thenReturn(List.of());

        dispatcher.dispatch();

        verify(messagePushDispatchService).dispatchSentMessage(any(MessageSentPublishRequest.class));
        verify(imMessageBridgeService).syncSentMessage(any(MessageSentPublishRequest.class));
        verify(imMessageBridgeService).syncRecallMessage(any(MessageRecallSyncRequest.class));
        verify(messageOutboxTaskMapper, org.mockito.Mockito.times(3)).updateById(any(MessageOutboxTaskPO.class));
    }

    @Test
    @DisplayName("派发失败时应该增加重试次数并切换到 FAILED")
    void shouldMarkTaskFailedWhenDispatchThrows() {
        MessageOutboxProperties properties = new MessageOutboxProperties();
        MessageOutboxDispatcher dispatcher = new MessageOutboxDispatcher(
                messageOutboxTaskMapper,
                properties,
                messagePushDispatchService,
                imMessageBridgeService,
                directExecutor(),
                immediateTransactions()
        );
        MessageSentPublishRequest sentRequest = MessageSentPublishRequest.from(
                Message.createText(1L, 2L, 3L, 4L, "hello")
        );
        MessageOutboxTaskPO task = task(1L, 2L, MessageOutboxTaskStatus.PENDING,
                MessageOutboxTaskType.MESSAGE_PUSH, JsonUtils.toJson(sentRequest));

        when(messageOutboxTaskMapper.claimDispatchable(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("mq failed"))
                .when(messagePushDispatchService).dispatchSentMessage(any(MessageSentPublishRequest.class));

        dispatcher.dispatch();

        ArgumentCaptor<MessageOutboxTaskPO> captor = ArgumentCaptor.forClass(MessageOutboxTaskPO.class);
        verify(messageOutboxTaskMapper).updateById(captor.capture());
        assertEquals(MessageOutboxTaskStatus.FAILED.name(), captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
        assertNull(captor.getValue().getClaimedAt());
        assertNull(captor.getValue().getClaimedBy());
    }

    @Test
    @DisplayName("超过最大重试次数时应该标记为 DEAD")
    void shouldMarkTaskDeadWhenRetryExhausted() {
        MessageOutboxProperties properties = new MessageOutboxProperties();
        properties.setMaxRetry(1);
        MessageOutboxDispatcher dispatcher = new MessageOutboxDispatcher(
                messageOutboxTaskMapper,
                properties,
                messagePushDispatchService,
                imMessageBridgeService,
                directExecutor(),
                immediateTransactions()
        );
        MessageSentPublishRequest sentRequest = MessageSentPublishRequest.from(
                Message.createText(11L, 22L, 33L, 44L, "hello")
        );
        MessageOutboxTaskPO task = task(1L, 22L, MessageOutboxTaskStatus.PENDING,
                MessageOutboxTaskType.MESSAGE_PUSH, JsonUtils.toJson(sentRequest));

        when(messageOutboxTaskMapper.claimDispatchable(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("push failed"))
                .when(messagePushDispatchService).dispatchSentMessage(any(MessageSentPublishRequest.class));

        dispatcher.dispatch();

        ArgumentCaptor<MessageOutboxTaskPO> captor = ArgumentCaptor.forClass(MessageOutboxTaskPO.class);
        verify(messageOutboxTaskMapper).updateById(captor.capture());
        assertEquals(MessageOutboxTaskStatus.DEAD.name(), captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
    }

    @Test
    @DisplayName("claim 到超时回收的 PROCESSING 任务后应该继续处理")
    void shouldDispatchReclaimedProcessingTask() {
        MessageOutboxProperties properties = new MessageOutboxProperties();
        MessageOutboxDispatcher dispatcher = new MessageOutboxDispatcher(
                messageOutboxTaskMapper,
                properties,
                messagePushDispatchService,
                imMessageBridgeService,
                directExecutor(),
                immediateTransactions()
        );
        MessageSentPublishRequest sentRequest = MessageSentPublishRequest.from(
                Message.createText(21L, 31L, 41L, 51L, "hello")
        );
        MessageOutboxTaskPO task = task(1L, 31L, MessageOutboxTaskStatus.PROCESSING,
                MessageOutboxTaskType.MESSAGE_PUSH, JsonUtils.toJson(sentRequest));

        when(messageOutboxTaskMapper.claimDispatchable(any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(task))
                .thenReturn(List.of());

        dispatcher.dispatch();

        ArgumentCaptor<MessageOutboxTaskPO> captor = ArgumentCaptor.forClass(MessageOutboxTaskPO.class);
        verify(messageOutboxTaskMapper).updateById(captor.capture());
        assertEquals(MessageOutboxTaskStatus.SUCCEEDED.name(), captor.getValue().getStatus());
        assertNull(captor.getValue().getClaimedAt());
        assertNull(captor.getValue().getClaimedBy());
    }

    private MessageOutboxTaskPO task(Long id,
                                     Long aggregateId,
                                     MessageOutboxTaskStatus status,
                                     MessageOutboxTaskType taskType,
                                     String payload) {
        MessageOutboxTaskPO task = new MessageOutboxTaskPO();
        task.setId(id);
        task.setTaskKey(taskType.name() + ":" + id);
        task.setTaskType(taskType.name());
        task.setAggregateId(aggregateId);
        task.setPayload(payload);
        task.setRetryCount(0);
        task.setStatus(status.name());
        task.setNextAttemptAt(OffsetDateTime.now());
        task.setClaimedAt(OffsetDateTime.now().minusMinutes(5));
        task.setClaimedBy("worker-1");
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        return task;
    }

    private TaskExecutor directExecutor() {
        return Runnable::run;
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
            }
        };
    }
}
