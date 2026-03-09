package com.zhicore.admin.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("AdminSentinelHandlers 测试")
class AdminSentinelHandlersTest {

    @Test
    @DisplayName("后台用户列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForUserList() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> AdminSentinelHandlers.handleListUsersBlocked(null, null, 1, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("后台举报列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForReportList() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> AdminSentinelHandlers.handleListReportsByStatusBlocked("PENDING", 1, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
