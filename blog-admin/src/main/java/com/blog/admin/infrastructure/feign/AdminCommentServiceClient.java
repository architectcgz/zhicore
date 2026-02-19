package com.blog.admin.infrastructure.feign;

import com.blog.common.result.ApiResponse;
import com.blog.common.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 管理服务调用评论服务的 Feign 客户端
 */
@FeignClient(
        name = "blog-comment",
        contextId = "adminCommentServiceClient",
        fallbackFactory = AdminCommentServiceFallbackFactory.class
)
public interface AdminCommentServiceClient {
    
    /**
     * 管理员查询评论列表
     */
    @GetMapping("/api/v1/admin/comments")
    ApiResponse<PageResult<CommentManageDTO>> queryComments(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "postId", required = false) Long postId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);
    
    /**
     * 管理员删除评论（软删除）
     */
    @DeleteMapping("/api/v1/admin/comments/{commentId}")
    ApiResponse<Void> deleteComment(@PathVariable("commentId") Long commentId);
    
    /**
     * 评论管理 DTO
     */
    record CommentManageDTO(
            Long id,
            Long postId,
            String postTitle,
            Long userId,
            String userName,
            String content,
            int likeCount,
            LocalDateTime createdAt
    ) {}
}
