package com.zhicore.content.application.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.query.model.PostListQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ContentSentinelHandlers 测试")
class ContentSentinelHandlersTest {

    @Test
    @DisplayName("文章详情 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForPostDetail() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleGetPostDetailBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("文章列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForPostList() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleGetPostListBlocked(PostListQuery.builder().build(), new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("文章内容 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForPostContent() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleGetPostContentBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("标签详情 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForTagDetail() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleGetTagDetailBlocked("java", new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("批量点赞状态 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForBatchLiked() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleBatchCheckPostLikedBlocked(1L, List.of(1L), new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("后台文章列表 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForAdminQueryPosts() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleAdminQueryPostsBlocked(null, null, null, 1, 20,
                        new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("死信事件分页 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForDeadOutbox() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> ContentSentinelHandlers.handleListDeadOutboxBlocked(1, 20, null, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
