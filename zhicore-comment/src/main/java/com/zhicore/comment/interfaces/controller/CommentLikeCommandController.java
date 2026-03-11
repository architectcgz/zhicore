package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.service.CommentLikeCommandService;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论点赞写控制器。
 */
@Tag(name = "评论点赞写接口", description = "评论点赞与取消点赞接口")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Validated
public class CommentLikeCommandController {

    private final CommentLikeCommandService commentLikeCommandService;

    @Operation(summary = "点赞评论", description = "为评论点赞。如果已经点赞过，则操作无效。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "点赞成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @PostMapping("/{commentId}/like")
    public ApiResponse<Void> likeComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        Long userId = UserContext.requireUserId();
        commentLikeCommandService.likeComment(userId, commentId);
        return ApiResponse.success();
    }

    @Operation(summary = "取消点赞评论", description = "取消对评论的点赞。如果未点赞过，则操作无效。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "取消点赞成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/{commentId}/like")
    public ApiResponse<Void> unlikeComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        Long userId = UserContext.requireUserId();
        commentLikeCommandService.unlikeComment(userId, commentId);
        return ApiResponse.success();
    }
}
