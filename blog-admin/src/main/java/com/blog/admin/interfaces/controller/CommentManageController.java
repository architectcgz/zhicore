package com.blog.admin.interfaces.controller;

import com.blog.admin.application.dto.CommentManageVO;
import com.blog.admin.application.service.CommentManageService;
import com.blog.admin.infrastructure.security.RequireAdmin;
import com.blog.admin.interfaces.dto.request.DeleteContentRequest;
import com.blog.common.result.ApiResponse;
import com.blog.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 评论管理控制器
 */
@Tag(name = "评论管理", description = "管理员评论管理相关接口，包括查询评论列表、删除评论等功能")
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class CommentManageController {
    
    private final CommentManageService commentManageService;
    
    /**
     * 查询评论列表
     */
    @Operation(
            summary = "查询评论列表",
            description = "分页查询评论列表，支持按关键词、文章ID、用户ID筛选"
    )
    @GetMapping
    public ApiResponse<PageResult<CommentManageVO>> listComments(
            @Parameter(description = "搜索关键词（评论内容）", example = "很好")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章ID", example = "2001")
            @RequestParam(value = "postId", required = false) Long postId,
            @Parameter(description = "用户ID", example = "1001")
            @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageResult<CommentManageVO> result = commentManageService.listComments(keyword, postId, userId, page, size);
        return ApiResponse.success(result);
    }
    
    /**
     * 删除评论
     */
    @Operation(
            summary = "删除评论",
            description = "管理员删除指定评论，需要提供删除原因"
    )
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要删除的评论ID", required = true, example = "3001")
            @PathVariable("commentId") @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "删除评论请求信息", required = true)
            @Valid @RequestBody DeleteContentRequest request) {
        commentManageService.deleteComment(adminId, commentId, request.getReason());
        return ApiResponse.success();
    }
}
