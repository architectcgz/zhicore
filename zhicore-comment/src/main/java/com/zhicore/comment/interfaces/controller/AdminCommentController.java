package com.zhicore.comment.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.comment.application.service.AdminCommentApplicationService;
import com.zhicore.comment.interfaces.dto.response.CommentManageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员评论管理控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "管理员-评论管理", description = "管理员评论管理功能，包括评论审核、评论删除、违规评论处理等功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminCommentApplicationService adminCommentApplicationService;

    /**
     * 查询评论列表
     */
    @Operation(
            summary = "查询评论列表",
            description = "管理员查询评论列表，支持按关键词、文章ID、用户ID筛选"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @GetMapping
    public ApiResponse<PageResult<CommentManageDTO>> queryComments(
            @Parameter(description = "关键词，搜索评论内容", example = "测试评论")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章ID，筛选指定文章的评论", example = "1234567890")
            @RequestParam(value = "postId", required = false) Long postId,
            @Parameter(description = "用户ID，筛选指定用户的评论", example = "1234567890")
            @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        log.info("Admin query comments: keyword={}, postId={}, userId={}, page={}, size={}", 
                keyword, postId, userId, page, size);
        
        PageResult<CommentManageDTO> result = adminCommentApplicationService.queryComments(keyword, postId, userId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 删除评论（软删除）
     */
    @Operation(
            summary = "删除评论",
            description = "管理员删除评论（软删除）。删除顶级评论会同时删除所有回复。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "删除成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "评论不存在"
            )
    })
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        log.info("Admin delete comment: commentId={}", commentId);
        
        adminCommentApplicationService.deleteComment(commentId);
        return ApiResponse.success();
    }
}
