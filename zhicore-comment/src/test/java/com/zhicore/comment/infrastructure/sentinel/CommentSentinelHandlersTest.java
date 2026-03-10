package com.zhicore.comment.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CommentSentinelHandlers 测试")
class CommentSentinelHandlersTest {

    @Test
    @DisplayName("评论详情 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForCommentDetail() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleGetCommentBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("顶级评论分页 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForTopLevelPage() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleGetTopLevelCommentsPageBlocked(
                        1L, 0, 20, CommentSortType.TIME, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("回复游标分页 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForRepliesCursor() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleGetRepliesCursorBlocked(
                        1L, null, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("点赞状态 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForIsLiked() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleIsCommentLikedBlocked(1L, 2L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("批量点赞状态 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForBatchLiked() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleBatchCheckCommentLikedBlocked(1L, List.of(1L, 2L), new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("点赞数 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForLikeCount() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleGetCommentLikeCountBlocked(1L, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("后台评论查询 block 时应该抛出 429 业务异常")
    void shouldThrowTooManyRequestsForAdminQueryComments() {
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
                () -> CommentSentinelHandlers.handleAdminQueryCommentsBlocked(
                        "spam", 1L, 2L, 1, 20, new FlowException("blocked")));

        assertEquals(ResultCode.TOO_MANY_REQUESTS.getCode(), exception.getCode());
    }
}
