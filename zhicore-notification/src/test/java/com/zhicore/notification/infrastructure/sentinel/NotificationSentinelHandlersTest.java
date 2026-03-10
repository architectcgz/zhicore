package com.zhicore.notification.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("NotificationSentinelHandlers 测试")
class NotificationSentinelHandlersTest {

    @Test
    @DisplayName("聚合通知 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForAggregatedNotifications() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> NotificationSentinelHandlers.handleAggregatedNotificationsBlocked(1L, 0, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("未读计数 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForUnreadCount() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> NotificationSentinelHandlers.handleUnreadCountBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
