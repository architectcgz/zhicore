package com.zhicore.message.infrastructure.push;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessagePushDispatchService 测试")
class MessagePushDispatchServiceTest {

    @Mock
    private MultiChannelPushService pushService;

    @Mock
    private UserServiceClient userServiceClient;

    @Test
    @DisplayName("派发消息时应该推送给接收者")
    void shouldPushMessageToReceiver() {
        MessagePushDispatchService service = new MessagePushDispatchService(pushService, userServiceClient);
        MessageSentPublishRequest request = MessageSentPublishRequest.from(
                Message.createText(11L, 22L, 33L, 44L, "hello")
        );
        UserSimpleDTO sender = new UserSimpleDTO();
        sender.setNickname("alice");
        sender.setAvatarId("avatar-1");

        when(userServiceClient.getUserSimple(33L)).thenReturn(ApiResponse.success(sender));

        service.dispatchSentMessage(request);

        ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).push(eq("44"), messageCaptor.capture());

        PushMessage pushMessage = messageCaptor.getValue();
        assertEquals(request.getMessageId(), pushMessage.getMessageId());
        assertEquals(request.getConversationId(), pushMessage.getConversationId());
        assertEquals(request.getSenderId(), pushMessage.getSenderId());
        assertEquals("alice", pushMessage.getSenderNickName());
        assertEquals("avatar-1", pushMessage.getSenderAvatarUrl());
        assertEquals(request.getContentPreview(), pushMessage.getContentPreview());
    }

    @Test
    @DisplayName("推送失败时应该抛出异常以便 Outbox 重试")
    void shouldRethrowWhenPushFails() {
        MessagePushDispatchService service = new MessagePushDispatchService(pushService, userServiceClient);
        MessageSentPublishRequest request = MessageSentPublishRequest.from(
                Message.createText(12L, 23L, 34L, 45L, "preview")
        );

        when(userServiceClient.getUserSimple(34L)).thenReturn(ApiResponse.success(null));
        doAnswer(invocation -> {
            throw new RuntimeException("push failed");
        }).when(pushService).push(eq("45"), any(PushMessage.class));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.dispatchSentMessage(request));

        assertEquals("push failed", exception.getMessage());
    }
}
