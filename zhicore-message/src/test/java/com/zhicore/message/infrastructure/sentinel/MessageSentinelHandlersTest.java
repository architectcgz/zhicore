package com.zhicore.message.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("MessageSentinelHandlers 测试")
class MessageSentinelHandlersTest {

    @Test
    @DisplayName("会话列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForConversationList() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> MessageSentinelHandlers.handleConversationListBlocked(null, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("消息历史 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForMessageHistory() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> MessageSentinelHandlers.handleMessageHistoryBlocked(1L, null, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("未读数 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForUnreadCount() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> MessageSentinelHandlers.handleUnreadCountBlocked(new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
