package com.zhicore.message.application.service;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.MessageVO;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageQueryService 测试")
class MessageQueryServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageDomainService messageDomainService;

    @InjectMocks
    private MessageQueryService messageQueryService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("查询消息历史时应该校验会话访问权限")
    void shouldValidateConversationAccessWhenGetMessageHistory() {
        Long userId = 101L;
        Long conversationId = 1001L;
        UserContext.setUser(new UserContext.UserInfo(String.valueOf(userId), "sender"));

        Conversation conversation = Conversation.create(conversationId, userId, 202L);
        Message message = Message.reconstitute(
                2002L,
                conversationId,
                userId,
                202L,
                MessageType.TEXT,
                "hello",
                null,
                true,
                LocalDateTime.now(),
                MessageStatus.SENT,
                LocalDateTime.now().minusMinutes(1)
        );

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationId(conversationId, null, 20)).thenReturn(List.of(message));

        List<MessageVO> result = messageQueryService.getMessageHistory(conversationId, null, 20);

        verify(messageDomainService).validateConversationAccess(conversation, userId);
        assertEquals(1, result.size());
        assertEquals(message.getId(), result.get(0).getId());
        assertEquals(conversationId, result.get(0).getConversationId());
    }

    @Test
    @DisplayName("会话不存在时查询消息历史应该失败")
    void shouldFailWhenConversationMissing() {
        UserContext.setUser(new UserContext.UserInfo("101", "sender"));
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> messageQueryService.getMessageHistory(999L, null, 20));

        assertEquals(ResultCode.CONVERSATION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("获取未读消息数时应该按当前用户查询")
    void shouldCountUnreadByCurrentUser() {
        UserContext.setUser(new UserContext.UserInfo("101", "sender"));
        when(messageRepository.countUnreadByUserId(101L)).thenReturn(7);

        int count = messageQueryService.getUnreadCount();

        assertEquals(7, count);
    }
}
