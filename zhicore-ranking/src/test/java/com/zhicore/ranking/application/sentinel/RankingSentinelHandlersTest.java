package com.zhicore.ranking.application.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RankingSentinelHandlers 测试")
class RankingSentinelHandlersTest {

    @Test
    @DisplayName("热门文章详情 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForHotPostDetails() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> RankingSentinelHandlers.handleHotPostDetailsBlocked(0, 10, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("文章元数据解析 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForResolvePostMetadata() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> RankingSentinelHandlers.handleResolvePostMetadataBlocked(List.of(1L), new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
