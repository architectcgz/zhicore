package com.zhicore.api.client;

import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理侧文章服务契约。
 */
public interface AdminPostClient {

    @GetMapping("/api/v1/admin/posts")
    ApiResponse<PageResult<PostManageDTO>> queryPosts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "authorId", required = false) Long authorId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @DeleteMapping("/api/v1/admin/posts/{postId}")
    ApiResponse<Void> deletePost(@PathVariable("postId") Long postId);
}
