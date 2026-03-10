package com.zhicore.idgenerator.service.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("IdGeneratorSentinelHandlers 测试")
class IdGeneratorSentinelHandlersTest {

    @Test
    @DisplayName("单个 Snowflake block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForSnowflake() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> IdGeneratorSentinelHandlers.handleGenerateSnowflakeIdBlocked(new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("Segment block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForSegment() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> IdGeneratorSentinelHandlers.handleGenerateSegmentIdBlocked("user",
                        new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
