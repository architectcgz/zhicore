package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.service.query.CommentLikeQueryService;
import com.zhicore.comment.interfaces.dto.request.BatchCheckLikedRequest;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评论点赞读控制器。
 */
@Tag(name = "评论点赞读接口", description = "评论点赞状态与计数查询接口")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Validated
public class CommentLikeQueryController {

    private final CommentLikeQueryService commentLikeQueryService;

    @Operation(summary = "检查是否已点赞", description = "检查当前用户是否已对指定评论点赞")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功，返回点赞状态",
                    content = @Content(schema = @Schema(implementation = Boolean.class))
            )
    })
    @GetMapping("/{commentId}/liked")
    public ApiResponse<Boolean> isLiked(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(commentLikeQueryService.isLiked(userId, commentId));
    }

    @Operation(summary = "批量检查点赞状态", description = "批量检查当前用户对多条评论的点赞状态，返回评论ID到点赞状态的映射")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功，返回点赞状态映射")
    })
    @PostMapping("/batch/liked")
    public ApiResponse<Map<Long, Boolean>> batchCheckLiked(
            @Parameter(description = "批量检查点赞状态请求", required = true)
            @RequestBody @Valid BatchCheckLikedRequest request) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(commentLikeQueryService.batchCheckLiked(userId, request.getCommentIds()));
    }

    @Operation(summary = "获取评论点赞数", description = "获取指定评论的总点赞数")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功，返回点赞数",
                    content = @Content(schema = @Schema(implementation = Integer.class))
            )
    })
    @GetMapping("/{commentId}/like-count")
    public ApiResponse<Integer> getLikeCount(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        return ApiResponse.success(commentLikeQueryService.getLikeCount(commentId));
    }
}
