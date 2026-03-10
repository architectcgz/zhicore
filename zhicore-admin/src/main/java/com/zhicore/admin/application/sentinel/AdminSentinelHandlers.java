package com.zhicore.admin.application.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.admin.application.dto.CommentManageVO;
import com.zhicore.admin.application.dto.PostManageVO;
import com.zhicore.admin.application.dto.ReportVO;
import com.zhicore.admin.application.dto.UserManageVO;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.PageResult;

/**
 * 管理服务 Sentinel 方法级 block 处理器。
 */
public final class AdminSentinelHandlers {

    private AdminSentinelHandlers() {
    }

    public static PageResult<UserManageVO> handleListUsersBlocked(
            String keyword, String status, int page, int size, BlockException ex) {
        throw tooManyRequests("后台用户列表请求过于频繁，请稍后重试");
    }

    public static PageResult<PostManageVO> handleListPostsBlocked(
            String keyword, String status, Long authorId, int page, int size, BlockException ex) {
        throw tooManyRequests("后台文章列表请求过于频繁，请稍后重试");
    }

    public static PageResult<CommentManageVO> handleListCommentsBlocked(
            String keyword, Long postId, Long userId, int page, int size, BlockException ex) {
        throw tooManyRequests("后台评论列表请求过于频繁，请稍后重试");
    }

    public static PageResult<ReportVO> handleListPendingReportsBlocked(int page, int size, BlockException ex) {
        throw tooManyRequests("后台举报列表请求过于频繁，请稍后重试");
    }

    public static PageResult<ReportVO> handleListReportsByStatusBlocked(
            String status, int page, int size, BlockException ex) {
        throw tooManyRequests("后台举报查询请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
