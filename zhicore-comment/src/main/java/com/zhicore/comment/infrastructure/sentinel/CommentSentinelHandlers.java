package com.zhicore.comment.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.dto.CursorPage;
import com.zhicore.comment.interfaces.dto.response.CommentManageDTO;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 评论服务 Sentinel 方法级 block 处理器。
 */
public final class CommentSentinelHandlers {

    private CommentSentinelHandlers() {
    }

    public static CommentVO handleGetCommentBlocked(Long commentId, BlockException ex) {
        throw tooManyRequests("评论详情请求过于频繁，请稍后重试");
    }

    public static PageResult<CommentVO> handleGetTopLevelCommentsPageBlocked(
            Long postId, int page, int size, CommentSortType sortType, BlockException ex) {
        throw tooManyRequests("评论列表请求过于频繁，请稍后重试");
    }

    public static CursorPage<CommentVO> handleGetTopLevelCommentsCursorBlocked(
            Long postId, String cursor, int size, CommentSortType sortType, BlockException ex) {
        throw tooManyRequests("评论列表请求过于频繁，请稍后重试");
    }

    public static PageResult<CommentVO> handleGetRepliesPageBlocked(
            Long rootId, int page, int size, BlockException ex) {
        throw tooManyRequests("回复列表请求过于频繁，请稍后重试");
    }

    public static CursorPage<CommentVO> handleGetRepliesCursorBlocked(
            Long rootId, String cursor, int size, BlockException ex) {
        throw tooManyRequests("回复列表请求过于频繁，请稍后重试");
    }

    public static boolean handleIsCommentLikedBlocked(Long userId, Long commentId, BlockException ex) {
        throw tooManyRequests("评论点赞状态请求过于频繁，请稍后重试");
    }

    public static Map<Long, Boolean> handleBatchCheckCommentLikedBlocked(
            Long userId, List<Long> commentIds, BlockException ex) {
        throw tooManyRequests("批量点赞状态请求过于频繁，请稍后重试");
    }

    public static int handleGetCommentLikeCountBlocked(Long commentId, BlockException ex) {
        throw tooManyRequests("评论点赞数请求过于频繁，请稍后重试");
    }

    public static PageResult<CommentManageDTO> handleAdminQueryCommentsBlocked(
            String keyword, Long postId, Long userId, int page, int size, BlockException ex) {
        throw tooManyRequests("后台评论查询请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
