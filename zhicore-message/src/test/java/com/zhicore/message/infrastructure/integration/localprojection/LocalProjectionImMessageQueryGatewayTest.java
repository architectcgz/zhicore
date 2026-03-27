package com.zhicore.message.infrastructure.integration.localprojection;

import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.domain.repository.ConversationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalProjectionImMessageQueryGateway 测试")
class LocalProjectionImMessageQueryGatewayTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Test
    @DisplayName("默认历史查询适配器在过渡阶段应该返回空列表")
    void shouldReturnEmptyHistoryDuringTransition() {
        Long userId = 101L;
        DirectConversationRef conversationRef = DirectConversationRef.of(101L, 202L);
        var gateway = new LocalProjectionImMessageQueryGateway(conversationRepository);

        assertEquals(List.of(), gateway.getMessageHistory(userId, conversationRef, null, 20));
    }

    @Test
    @DisplayName("默认未读数查询在过渡阶段不应继续依赖本地消息明细")
    void shouldNotDependOnLocalMessagesForUnreadCountDuringTransition() {
        var gateway = new LocalProjectionImMessageQueryGateway(conversationRepository);
        when(conversationRepository.countUnreadByUserId(101L)).thenReturn(0);

        assertEquals(0, gateway.countUnreadMessages(101L));
    }
}
