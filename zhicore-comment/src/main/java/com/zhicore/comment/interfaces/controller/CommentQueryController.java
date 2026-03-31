package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.dto.CursorPage;
import com.zhicore.comment.application.service.query.CommentQueryService;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论读控制器。
 */
@Tag(name = "评论读接口", description = "评论详情与列表查询接口")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Validated
public class CommentQueryController {

    private final CommentQueryService commentQueryService;

    @Operation(summary = "获取评论详情", description = "根据评论ID获取评论详细信息")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = CommentVO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @GetMapping("/{commentId}")
    public ApiResponse<CommentVO> getComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        return ApiResponse.success(commentQueryService.getComment(commentId));
    }

    @Operation(summary = "获取文章评论（传统分页）", description = "获取文章的顶级评论列表，使用传统分页方式。适用于Web端。支持按时间或热度排序。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/post/{postId}/page")
    public ApiResponse<PageResult<CommentVO>> getCommentsByPage(
            @Parameter(description = "文章ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不能为负数") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页大小必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页大小不能大于100") int size,
            @Parameter(description = "排序方式：TIME-按时间排序，HOT-按热度排序", example = "TIME")
            @RequestParam(defaultValue = "TIME") CommentSortType sort) {
        return ApiResponse.success(commentQueryService.getTopLevelCommentsByPage(postId, page, size, sort));
    }

    @Operation(summary = "获取文章评论（游标分页）", description = "获取文章的顶级评论列表，使用游标分页方式。适用于移动端无限滚动场景。支持按时间或热度排序。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/post/{postId}/cursor")
    public ApiResponse<CursorPage<CommentVO>> getCommentsByCursor(
            @Parameter(description = "文章ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "游标，首次请求不传", example = "eyJpZCI6MTIzNDU2Nzg5MCwidGltZXN0YW1wIjoxNjQwMDAwMDAwfQ==")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页大小必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页大小不能大于100") int size,
            @Parameter(description = "排序方式：TIME-按时间排序，HOT-按热度排序", example = "TIME")
            @RequestParam(defaultValue = "TIME") CommentSortType sort) {
        return ApiResponse.success(commentQueryService.getTopLevelCommentsByCursor(postId, cursor, size, sort));
    }

    @Operation(summary = "获取评论回复（传统分页）", description = "获取某条评论的所有回复，使用传统分页方式。适用于Web端。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/{commentId}/replies/page")
    public ApiResponse<PageResult<CommentVO>> getRepliesByPage(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不能为负数") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页大小必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页大小不能大于100") int size) {
        return ApiResponse.success(commentQueryService.getRepliesByPage(commentId, page, size));
    }

    @Operation(summary = "获取评论回复（游标分页）", description = "获取某条评论的所有回复，使用游标分页方式。适用于移动端无限滚动场景。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/{commentId}/replies/cursor")
    public ApiResponse<CursorPage<CommentVO>> getRepliesByCursor(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "游标，首次请求不传", example = "eyJpZCI6MTIzNDU2Nzg5MCwidGltZXN0YW1wIjoxNjQwMDAwMDAwfQ==")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页大小必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页大小不能大于100") int size) {
        return ApiResponse.success(commentQueryService.getRepliesByCursor(commentId, cursor, size));
    }

    @Operation(summary = "增量获取文章评论", description = "获取比当前游标更新的顶级评论列表，用于实时补拉。")
    @GetMapping("/post/{postId}/incremental")
    public ApiResponse<PageResult<CommentVO>> getCommentsIncremental(
            @Parameter(description = "文章ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "上次已知最新评论创建时间", example = "2026-03-27T11:00:00")
            @RequestParam(required = false) String afterCreatedAt,
            @Parameter(description = "上次已知最新评论ID", example = "1234567890")
            @RequestParam(required = false) @Min(value = 1, message = "评论ID必须为正数") Long afterId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页大小必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页大小不能大于100") int size) {
        return ApiResponse.success(commentQueryService.getTopLevelCommentsIncremental(postId, afterCreatedAt, afterId, size));
    }

    @Operation(summary = "增量获取评论回复", description = "获取比当前游标更新的回复列表，用于实时补拉。")
    @GetMapping("/{rootId}/replies/incremental")
    public ApiResponse<PageResult<CommentVO>> getRepliesIncremental(
            @Parameter(description = "顶级评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long rootId,
            @Parameter(description = "上次已知最新回复创建时间", example = "2026-03-27T11:00:00")
            @RequestParam(required = false) String afterCreatedAt,
            @Parameter(description = "上次已知最新回复ID", example = "1234567890")
            @RequestParam(required = false) @Min(value = 1, message = "评论ID必须为正数") Long afterId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页大小必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页大小不能大于100") int size) {
        return ApiResponse.success(commentQueryService.getRepliesIncremental(rootId, afterCreatedAt, afterId, size));
    }
}
