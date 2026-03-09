package com.zhicore.message.infrastructure.mq;

import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.domain.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageSentTransactionListener 测试")
class MessageSentTransactionListenerTest {

    @Mock
    private MessageEventPublisher messageEventPublisher;

    @Test
    @DisplayName("事务提交后应该委托 MQ 发布器发送消息事件")
    void shouldDelegatePublishAfterCommit() {
        MessageSentTransactionListener listener = new MessageSentTransactionListener(messageEventPublisher);
        Message message = Message.createText(11L, 22L, 33L, 44L, "listener test");
        MessageSentPublishRequest request = MessageSentPublishRequest.from(message);

        listener.onMessageSent(request);

        verify(messageEventPublisher).publishMessageSent(request);
    }
}
