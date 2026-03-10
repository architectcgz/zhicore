package com.zhicore.api.client;

import com.zhicore.api.dto.admin.CommentManageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理侧评论服务契约。
 */
public interface AdminCommentClient {

    @GetMapping("/api/v1/admin/comments")
    ApiResponse<PageResult<CommentManageDTO>> queryComments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "postId", required = false) Long postId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @DeleteMapping("/api/v1/admin/comments/{commentId}")
    ApiResponse<Void> deleteComment(@PathVariable("commentId") Long commentId);
}
