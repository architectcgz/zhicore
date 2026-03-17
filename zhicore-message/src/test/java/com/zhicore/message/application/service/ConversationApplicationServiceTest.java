package com.zhicore.message.application.service;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImConversationView;
import com.zhicore.message.application.port.im.ImConversationQueryGateway;
import com.zhicore.message.application.port.user.UserMessagingPort;
import com.zhicore.message.application.service.query.ConversationQueryService;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationQueryService 单元测试")
class ConversationApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long CONVERSATION_ID = 1001L;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageDomainService messageDomainService;

    @Mock
    private UserMessagingPort userMessagingPort;

    @Mock
    private ImConversationQueryGateway imConversationQueryGateway;

    private ConversationQueryService service;

    @BeforeEach
    void setUp() {
        UserContext.setUser(new UserContext.UserInfo(String.valueOf(USER_ID), "tester"));
        service = new ConversationQueryService(
                conversationRepository,
                messageDomainService,
                userMessagingPort,
                imConversationQueryGateway
        );
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("会话列表组装时用户服务降级应显式失败")
    void shouldFailConversationListWhenUserServiceDegraded() {
        when(imConversationQueryGateway.listConversations(USER_ID, null, 20))
                .thenReturn(List.of(createConversationView()));
        when(userMessagingPort.getUserSimple(OTHER_USER_ID))
                .thenThrow(new RuntimeException("用户服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getConversationList(null, 20));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("用户服务已降级", exception.getMessage());
    }

    @Test
    @DisplayName("会话详情组装时用户服务降级应显式失败")
    void shouldFailConversationDetailWhenUserServiceDegraded() {
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(createConversation()));
        when(imConversationQueryGateway.findConversation(USER_ID, DirectConversationRef.of(USER_ID, OTHER_USER_ID)))
                .thenReturn(Optional.of(createConversationView()));
        when(userMessagingPort.getUserSimple(OTHER_USER_ID))
                .thenThrow(new RuntimeException("用户服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getConversation(CONVERSATION_ID));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("用户服务已降级", exception.getMessage());
    }

    private Conversation createConversation() {
        return Conversation.reconstitute(
                CONVERSATION_ID,
                USER_ID,
                OTHER_USER_ID,
                9001L,
                "hello",
                LocalDateTime.now(),
                0,
                1,
                LocalDateTime.now()
        );
    }

    private ImConversationView createConversationView() {
        return ImConversationView.builder()
                .localConversationId(CONVERSATION_ID)
                .conversationRef(DirectConversationRef.of(USER_ID, OTHER_USER_ID))
                .lastMessageContent("hello")
                .lastMessageAt(LocalDateTime.now())
                .unreadCount(1)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
