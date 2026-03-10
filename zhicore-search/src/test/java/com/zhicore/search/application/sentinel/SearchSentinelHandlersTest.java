package com.zhicore.search.application.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SearchSentinelHandlers 测试")
class SearchSentinelHandlersTest {

    @Test
    @DisplayName("搜索文章 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForSearchPosts() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> SearchSentinelHandlers.handleSearchPostsBlocked("spring", 0, 10, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
