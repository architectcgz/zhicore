package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.dto.CursorPage;
import com.zhicore.comment.application.service.CommentApplicationService;
import com.zhicore.comment.interfaces.dto.request.CreateCommentRequest;
import com.zhicore.comment.interfaces.dto.request.UpdateCommentRequest;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "评论管理", description = "评论的创建、更新、删除、查询等功能")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentApplicationService commentService;

    // ==================== 创建评论 ====================

    /**
     * 创建评论
     */
    @Operation(
            summary = "创建评论",
            description = "创建新评论或回复已有评论。支持顶级评论和嵌套回复。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "创建成功，返回评论ID",
                    content = @Content(schema = @Schema(implementation = Long.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败"
            )
    })
    @PostMapping
    public ApiResponse<Long> createComment(
            @Parameter(description = "评论创建请求", required = true)
            @RequestBody @Valid CreateCommentRequest request) {
        Long userId = Long.valueOf(UserContext.getUserId());
        Long commentId = commentService.createComment(userId, request);
        return ApiResponse.success(commentId);
    }

    // ==================== 更新评论 ====================

    /**
     * 更新评论
     */
    @Operation(
            summary = "更新评论",
            description = "更新评论内容。只有评论作者可以更新自己的评论。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "更新成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "评论不存在"
            )
    })
    @PutMapping("/{commentId}")
    public ApiResponse<Void> updateComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "评论更新请求", required = true)
            @RequestBody @Valid UpdateCommentRequest request) {
        commentService.updateComment(commentId, request);
        return ApiResponse.success();
    }

    // ==================== 删除评论 ====================

    /**
     * 删除评论
     */
    @Operation(
            summary = "删除评论",
            description = "删除评论。只有评论作者可以删除自己的评论。删除顶级评论会同时删除所有回复。"
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
        commentService.deleteComment(commentId);
        return ApiResponse.success();
    }

    // ==================== 获取评论详情 ====================

    /**
     * 获取评论详情
     */
    @Operation(
            summary = "获取评论详情",
            description = "根据评论ID获取评论详细信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = CommentVO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "评论不存在"
            )
    })
    @GetMapping("/{commentId}")
    public ApiResponse<CommentVO> getComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        CommentVO comment = commentService.getComment(commentId);
        return ApiResponse.success(comment);
    }

    // ==================== 顶级评论查询 ====================

    /**
     * 【Web端】获取文章评论 - 传统分页
     */
    @Operation(
            summary = "获取文章评论（传统分页）",
            description = "获取文章的顶级评论列表，使用传统分页方式。适用于Web端。支持按时间或热度排序。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @GetMapping("/post/{postId}/page")
    public ApiResponse<PageResult<CommentVO>> getCommentsByPage(
            @Parameter(description = "文章ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序方式：TIME-按时间排序，HOT-按热度排序", example = "TIME")
            @RequestParam(defaultValue = "TIME") CommentSortType sort) {
        PageResult<CommentVO> result = commentService.getTopLevelCommentsByPage(postId, page, size, sort);
        return ApiResponse.success(result);
    }

    /**
     * 【移动端】获取文章评论 - 游标分页
     */
    @Operation(
            summary = "获取文章评论（游标分页）",
            description = "获取文章的顶级评论列表，使用游标分页方式。适用于移动端无限滚动场景。支持按时间或热度排序。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @GetMapping("/post/{postId}/cursor")
    public ApiResponse<CursorPage<CommentVO>> getCommentsByCursor(
            @Parameter(description = "文章ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "游标，首次请求不传", example = "eyJpZCI6MTIzNDU2Nzg5MCwidGltZXN0YW1wIjoxNjQwMDAwMDAwfQ==")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序方式：TIME-按时间排序，HOT-按热度排序", example = "TIME")
            @RequestParam(defaultValue = "TIME") CommentSortType sort) {
        CursorPage<CommentVO> result = commentService.getTopLevelCommentsByCursor(postId, cursor, size, sort);
        return ApiResponse.success(result);
    }

    // ==================== 回复列表查询 ====================

    /**
     * 【Web端】获取评论回复 - 传统分页
     */
    @Operation(
            summary = "获取评论回复（传统分页）",
            description = "获取某条评论的所有回复，使用传统分页方式。适用于Web端。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @GetMapping("/{commentId}/replies/page")
    public ApiResponse<PageResult<CommentVO>> getRepliesByPage(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        PageResult<CommentVO> result = commentService.getRepliesByPage(commentId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 【移动端】获取评论回复 - 游标分页
     */
    @Operation(
            summary = "获取评论回复（游标分页）",
            description = "获取某条评论的所有回复，使用游标分页方式。适用于移动端无限滚动场景。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @GetMapping("/{commentId}/replies/cursor")
    public ApiResponse<CursorPage<CommentVO>> getRepliesByCursor(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "游标，首次请求不传", example = "eyJpZCI6MTIzNDU2Nzg5MCwidGltZXN0YW1wIjoxNjQwMDAwMDAwfQ==")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        CursorPage<CommentVO> result = commentService.getRepliesByCursor(commentId, cursor, size);
        return ApiResponse.success(result);
    }
}
