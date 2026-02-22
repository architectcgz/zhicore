package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 管理服务调用文章服务的 Feign 客户端
 */
@FeignClient(
        name = "zhicore-content",
        contextId = "adminPostServiceClient",
        fallbackFactory = AdminPostServiceFallbackFactory.class
)
public interface AdminPostServiceClient {
    
    /**
     * 管理员查询文章列表
     */
    @GetMapping("/api/v1/admin/posts")
    ApiResponse<PageResult<PostManageDTO>> queryPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "authorId", required = false) Long authorId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);
    
    /**
     * 管理员删除文章（软删除）
     */
    @DeleteMapping("/api/v1/admin/posts/{postId}")
    ApiResponse<Void> deletePost(@PathVariable("postId") Long postId);
    
    /**
     * 文章管理 DTO
     */
    record PostManageDTO(
            Long id,
            String title,
            Long authorId,
            String authorName,
            String status,
            int viewCount,
            int likeCount,
            int commentCount,
            LocalDateTime createdAt,
            LocalDateTime publishedAt
    ) {}
}
