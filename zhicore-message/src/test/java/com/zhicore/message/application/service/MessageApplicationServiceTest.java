package com.zhicore.message.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.model.Message;
import com.zhicore.message.domain.model.MessageStatus;
import com.zhicore.message.domain.model.MessageType;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.repository.MessageRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageCommandService 测试")
class MessageApplicationServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageDomainService messageDomainService;

    @Mock
    private MessageRestrictionService messageRestrictionService;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @Captor
    private ArgumentCaptor<MessageSentPublishRequest> requestCaptor;

    @InjectMocks
    private MessageCommandService messageApplicationService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @DisplayName("发送文本消息时应该在事务内只发布提交后事件快照")
    void shouldPublishAfterCommitRequestWhenSendTextMessage() {
        Long senderId = 101L;
        Long receiverId = 202L;
        Long conversationId = 1001L;
        Long messageId = 2002L;
        String content = "hello message";

        UserContext.UserInfo userInfo = new UserContext.UserInfo(String.valueOf(senderId), "sender");
        UserContext.setUser(userInfo);

        Conversation conversation = Conversation.create(conversationId, senderId, receiverId);

        when(idGeneratorFeignClient.generateSnowflakeId())
                .thenReturn(ApiResponse.success(conversationId))
                .thenReturn(ApiResponse.success(messageId));
        when(messageDomainService.getOrCreateConversation(conversationId, senderId, receiverId))
                .thenReturn(conversation);
        doNothing().when(messageRestrictionService).checkCanSendMessage(senderId, receiverId);

        MessageVO result = messageApplicationService.sendTextMessage(receiverId, content);

        verify(messageRepository).save(messageCaptor.capture());
        Message savedMessage = messageCaptor.getValue();
        assertNotNull(savedMessage);
        assertEquals(messageId, savedMessage.getId());
        assertEquals(conversationId, savedMessage.getConversationId());
        assertEquals(senderId, savedMessage.getSenderId());
        assertEquals(receiverId, savedMessage.getReceiverId());
        assertEquals(content, savedMessage.getContent());

        verify(messageDomainService).updateConversationLastMessage(conversation, savedMessage);
        verify(applicationEventPublisher).publishEvent(requestCaptor.capture());

        MessageSentPublishRequest request = requestCaptor.getValue();
        assertNotNull(request);
        assertEquals(messageId, request.getMessageId());
        assertEquals(conversationId, request.getConversationId());
        assertEquals(senderId, request.getSenderId());
        assertEquals(receiverId, request.getReceiverId());
        assertEquals(savedMessage.getType(), request.getMessageType());
        assertEquals(savedMessage.getCreatedAt(), request.getSentAt());
        assertEquals(savedMessage.getPreviewContent(100), request.getContentPreview());

        assertEquals(messageId, result.getId());
        assertEquals(conversationId, result.getConversationId());
        assertEquals(senderId, result.getSenderId());
        assertEquals(receiverId, result.getReceiverId());
        assertEquals(content, result.getContent());
        assertTrue(result.isSelf());
    }

    @Test
    @DisplayName("撤回不存在的消息时应该返回消息不存在错误码")
    void shouldReturnMessageNotFoundCodeWhenRecallMissingMessage() {
        UserContext.setUser(new UserContext.UserInfo("101", "sender"));
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageApplicationService.recallMessage(999L));

        assertEquals(ResultCode.MESSAGE_NOT_FOUND.getCode(), exception.getCode());
        assertEquals(ResultCode.MESSAGE_NOT_FOUND.getMessage(), exception.getMessage());
    }

    @Test
    @DisplayName("撤回超时消息时应该返回撤回超时错误码")
    void shouldReturnRecallTimeoutCodeWhenRecallExpiredMessage() {
        Long userId = 101L;
        Long messageId = 2002L;
        UserContext.setUser(new UserContext.UserInfo(String.valueOf(userId), "sender"));
        Message message = Message.reconstitute(
                messageId,
                1001L,
                userId,
                202L,
                MessageType.TEXT,
                "expired message",
                null,
                false,
                null,
                MessageStatus.SENT,
                LocalDateTime.now().minusMinutes(3)
        );
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageApplicationService.recallMessage(messageId));

        assertEquals(ResultCode.MESSAGE_RECALL_TIMEOUT.getCode(), exception.getCode());
        assertEquals("消息发送超过2分钟，无法撤回", exception.getMessage());
        verify(messageRepository, never()).update(any());
    }
}
