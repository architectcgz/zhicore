package com.zhicore.user.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("UserSentinelHandlers 测试")
class UserSentinelHandlersTest {

    @Test
    @DisplayName("用户详情 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForUserDetail() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleGetUserDetailBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("批量用户简要信息 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForBatchUserSimple() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleBatchGetUsersSimpleBlocked(Set.of(1L, 2L), new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("陌生人消息设置 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForStrangerMessageSetting() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleGetStrangerMessageSettingBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("粉丝列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForFollowers() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleGetFollowersBlocked(1L, 1, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("关注统计 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForFollowStats() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleGetFollowStatsBlocked(1L, 2L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("月度签到位图 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForMonthlyCheckInBitmap() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleGetMonthlyCheckInBitmapBlocked(1L, YearMonth.of(2026, 3), new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("拉黑列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForBlockedUsers() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleGetBlockedUsersBlocked(1L, 1, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("拉黑关系查询 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForIsBlocked() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleIsBlockedBlocked(1L, 2L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("后台用户查询 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForQueryUsers() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserSentinelHandlers.handleQueryUsersBlocked("test", "ACTIVE", 1, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
