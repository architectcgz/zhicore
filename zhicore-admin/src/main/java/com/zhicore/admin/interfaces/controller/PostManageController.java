package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.PostManageVO;
import com.zhicore.admin.application.service.PostManageService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.admin.interfaces.dto.request.DeleteContentRequest;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 文章管理控制器
 */
@Tag(name = "文章管理", description = "管理员文章管理相关接口，包括查询文章列表、删除文章等功能")
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class PostManageController {
    
    private final PostManageService postManageService;
    
    /**
     * 查询文章列表
     */
    @Operation(
            summary = "查询文章列表",
            description = "分页查询文章列表，支持按关键词、状态、作者筛选"
    )
    @GetMapping
    public ApiResponse<PageResult<PostManageVO>> listPosts(
            @Parameter(description = "搜索关键词（标题或内容）", example = "Spring Boot")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章状态（PUBLISHED/DRAFT/DELETED）", example = "PUBLISHED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "作者用户ID", example = "1001")
            @RequestParam(value = "authorId", required = false) Long authorId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageResult<PostManageVO> result = postManageService.listPosts(keyword, status, authorId, page, size);
        return ApiResponse.success(result);
    }
    
    /**
     * 删除文章
     */
    @Operation(
            summary = "删除文章",
            description = "管理员删除指定文章，需要提供删除原因"
    )
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要删除的文章ID", required = true, example = "2001")
            @PathVariable("postId") @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "删除文章请求信息", required = true)
            @Valid @RequestBody DeleteContentRequest request) {
        postManageService.deletePost(adminId, postId, request.getReason());
        return ApiResponse.success();
    }
}
