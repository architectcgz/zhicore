package com.blog.comment.interfaces.controller;

import com.blog.comment.application.service.CommentLikeApplicationService;
import com.blog.common.context.UserContext;
import com.blog.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评论点赞控制器
 *
 * @author Blog Team
 */
@Tag(name = "评论点赞管理", description = "评论点赞功能，包括点赞评论、取消点赞、查询点赞状态、统计点赞数等功能")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentLikeController {

    private final CommentLikeApplicationService likeService;

    /**
     * 点赞评论
     */
    @Operation(
            summary = "点赞评论",
            description = "为评论点赞。如果已经点赞过，则操作无效。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "点赞成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "评论不存在"
            )
    })
    @PostMapping("/{commentId}/like")
    public ApiResponse<Void> likeComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        Long userId = Long.valueOf(UserContext.getUserId());
        likeService.likeComment(userId, commentId);
        return ApiResponse.success();
    }

    /**
     * 取消点赞评论
     */
    @Operation(
            summary = "取消点赞评论",
            description = "取消对评论的点赞。如果未点赞过，则操作无效。"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "取消点赞成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "评论不存在"
            )
    })
    @DeleteMapping("/{commentId}/like")
    public ApiResponse<Void> unlikeComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        Long userId = Long.valueOf(UserContext.getUserId());
        likeService.unlikeComment(userId, commentId);
        return ApiResponse.success();
    }

    /**
     * 检查是否已点赞
     */
    @Operation(
            summary = "检查是否已点赞",
            description = "检查当前用户是否已对指定评论点赞"
    )
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
        Long userId = Long.valueOf(UserContext.getUserId());
        boolean liked = likeService.isLiked(userId, commentId);
        return ApiResponse.success(liked);
    }

    /**
     * 批量检查点赞状态
     */
    @Operation(
            summary = "批量检查点赞状态",
            description = "批量检查当前用户对多条评论的点赞状态，返回评论ID到点赞状态的映射"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功，返回点赞状态映射"
            )
    })
    @PostMapping("/batch/liked")
    public ApiResponse<Map<Long, Boolean>> batchCheckLiked(
            @Parameter(description = "评论ID列表", required = true)
            @RequestBody List<Long> commentIds) {
        Long userId = Long.valueOf(UserContext.getUserId());
        Map<Long, Boolean> likedMap = likeService.batchCheckLiked(userId, commentIds);
        return ApiResponse.success(likedMap);
    }

    /**
     * 获取评论点赞数
     */
    @Operation(
            summary = "获取评论点赞数",
            description = "获取指定评论的总点赞数"
    )
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
        int count = likeService.getLikeCount(commentId);
        return ApiResponse.success(count);
    }
}
