package com.zhicore.message.infrastructure.mq;

import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.message.application.event.MessageRecallSyncRequest;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.infrastructure.config.ImBridgeProperties;
import com.zhicore.message.infrastructure.repository.mapper.MessageOutboxTaskMapper;
import com.zhicore.message.infrastructure.repository.po.MessageOutboxTaskPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageOutboxTransactionEventPublisher 测试")
class MessageOutboxTransactionEventPublisherTest {

    @Mock
    private MessageOutboxTaskMapper messageOutboxTaskMapper;

    @Mock
    private MessageOutboxDispatcher messageOutboxDispatcher;

    @Test
    @DisplayName("发送消息时应该写入 MQ 与 IM 两类 outbox 任务")
    void shouldWriteMqAndImTasksWhenSendMessage() {
        ImBridgeProperties properties = new ImBridgeProperties();
        properties.setEnabled(true);
        MessageOutboxTransactionEventPublisher publisher =
                new MessageOutboxTransactionEventPublisher(
                        messageOutboxTaskMapper,
                        properties,
                        new TransactionCommitSignal(),
                        messageOutboxDispatcher
                );
        MessageSentPublishRequest request = MessageSentPublishRequest.from(
                Message.createText(11L, 22L, 33L, 44L, "hello")
        );

        publisher.publishMessageSent(request);

        ArgumentCaptor<MessageOutboxTaskPO> captor = ArgumentCaptor.forClass(MessageOutboxTaskPO.class);
        verify(messageOutboxTaskMapper, times(2)).insert(captor.capture());
        List<MessageOutboxTaskPO> tasks = captor.getAllValues();
        assertEquals(MessageOutboxTaskType.MESSAGE_PUSH.name(), tasks.get(0).getTaskType());
        assertEquals(MessageOutboxTaskType.MESSAGE_SENT_IM.name(), tasks.get(1).getTaskType());
        assertEquals(22L, tasks.get(0).getAggregateId());
        assertEquals(22L, tasks.get(1).getAggregateId());
    }

    @Test
    @DisplayName("撤回消息时仅在 IM bridge 启用时写入 recall 任务")
    void shouldWriteRecallTaskOnlyWhenImBridgeEnabled() {
        ImBridgeProperties properties = new ImBridgeProperties();
        properties.setEnabled(true);
        MessageOutboxTransactionEventPublisher publisher =
                new MessageOutboxTransactionEventPublisher(
                        messageOutboxTaskMapper,
                        properties,
                        new TransactionCommitSignal(),
                        messageOutboxDispatcher
                );

        publisher.publishMessageRecalled(new MessageRecallSyncRequest(99L, 88L, 7L));

        ArgumentCaptor<MessageOutboxTaskPO> captor = ArgumentCaptor.forClass(MessageOutboxTaskPO.class);
        verify(messageOutboxTaskMapper).insert(captor.capture());
        assertEquals(MessageOutboxTaskType.MESSAGE_RECALL_IM.name(), captor.getValue().getTaskType());
        assertEquals(88L, captor.getValue().getAggregateId());
    }
}
