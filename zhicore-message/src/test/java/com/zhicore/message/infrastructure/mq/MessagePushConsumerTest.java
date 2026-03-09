package com.zhicore.message.infrastructure.mq;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.domain.event.MessageSentEvent;
import com.zhicore.message.domain.model.MessageType;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import com.zhicore.message.infrastructure.push.MultiChannelPushService;
import com.zhicore.message.infrastructure.push.PushMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessagePushConsumer 测试")
class MessagePushConsumerTest {

    @Mock
    private StatefulIdempotentHandler idempotentHandler;

    @Mock
    private MultiChannelPushService pushService;

    @Mock
    private UserServiceClient userServiceClient;

    @Test
    @DisplayName("消费消息时应该经过幂等处理并推送给接收者")
    void shouldPushMessageWithIdempotentGuard() {
        MessagePushConsumer consumer = new MessagePushConsumer(idempotentHandler, pushService, userServiceClient);
        MessageSentEvent event = new MessageSentEvent(
                11L,
                22L,
                33L,
                44L,
                MessageType.TEXT,
                "preview",
                LocalDateTime.of(2026, 3, 8, 19, 0)
        );
        UserSimpleDTO sender = new UserSimpleDTO();
        sender.setNickname("alice");
        sender.setAvatarId("avatar-1");

        when(userServiceClient.getUserSimple("33")).thenReturn(ApiResponse.success(sender));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));

        consumer.onMessage(JsonUtils.toJson(event));

        ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
        verify(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        verify(pushService).push(eq("44"), messageCaptor.capture());

        PushMessage pushMessage = messageCaptor.getValue();
        assertEquals(event.getMessageId(), pushMessage.getMessageId());
        assertEquals(event.getConversationId(), pushMessage.getConversationId());
        assertEquals(event.getSenderId(), pushMessage.getSenderId());
        assertEquals("alice", pushMessage.getSenderNickName());
        assertEquals("avatar-1", pushMessage.getSenderAvatarUrl());
        assertEquals(event.getContentPreview(), pushMessage.getContentPreview());
    }

    @Test
    @DisplayName("推送失败时应该抛出异常以便 RocketMQ 重试")
    void shouldRethrowWhenPushFails() {
        MessagePushConsumer consumer = new MessagePushConsumer(idempotentHandler, pushService, userServiceClient);
        MessageSentEvent event = new MessageSentEvent(
                12L,
                23L,
                34L,
                45L,
                MessageType.TEXT,
                "preview",
                LocalDateTime.of(2026, 3, 8, 19, 1)
        );

        when(userServiceClient.getUserSimple("34")).thenReturn(ApiResponse.success(null));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        doAnswer(invocation -> {
            throw new RuntimeException("push failed");
        }).when(pushService).push(eq("45"), any(PushMessage.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> consumer.onMessage(JsonUtils.toJson(event)));

        assertEquals("push failed", exception.getMessage());
        verify(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
    }
}
