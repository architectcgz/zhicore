package com.blog.post.interfaces.controller;

import com.blog.common.result.ApiResponse;
import com.blog.common.result.PageResult;
import com.blog.post.application.service.AdminPostApplicationService;
import com.blog.post.interfaces.dto.response.PostManageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员文章管理控制器
 *
 * @author Blog Team
 */
@Tag(name = "管理员-文章管理", description = "管理员文章管理功能，包括文章审核、文章删除、文章置顶、文章推荐等功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostApplicationService adminPostApplicationService;

    /**
     * 查询文章列表
     */
    @Operation(
            summary = "查询文章列表",
            description = "管理员分页查询文章列表，支持关键词搜索、状态筛选和作者筛选"
    )
    @GetMapping
    public ApiResponse<PageResult<PostManageDTO>> queryPosts(
            @Parameter(description = "搜索关键词（标题、内容等）", example = "Spring Boot")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章状态（DRAFT/PUBLISHED/DELETED）", example = "PUBLISHED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "作者ID", example = "1")
            @RequestParam(value = "authorId", required = false) Long authorId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        log.info("Admin query posts: keyword={}, status={}, authorId={}, page={}, size={}", 
                keyword, status, authorId, page, size);
        
        PageResult<PostManageDTO> result = adminPostApplicationService.queryPosts(keyword, status, authorId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 删除文章（软删除）
     */
    @Operation(
            summary = "删除文章",
            description = "管理员删除文章（软删除），删除后文章不再对外展示"
    )
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        log.info("Admin delete post: postId={}", postId);
        
        adminPostApplicationService.deletePost(postId);
        return ApiResponse.success();
    }
}
