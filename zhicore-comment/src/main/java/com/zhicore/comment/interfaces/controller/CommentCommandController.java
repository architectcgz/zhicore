package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.command.CreateCommentCommand;
import com.zhicore.comment.application.command.UpdateCommentCommand;
import com.zhicore.comment.application.service.command.CommentCommandService;
import com.zhicore.comment.interfaces.dto.request.CreateCommentRequest;
import com.zhicore.comment.interfaces.dto.request.UpdateCommentRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论写控制器。
 */
@Tag(name = "评论写接口", description = "评论创建、更新、删除接口")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Validated
public class CommentCommandController {

    private final CommentCommandService commentCommandService;

    @Operation(summary = "创建评论", description = "创建新评论或回复已有评论。支持顶级评论和嵌套回复。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "创建成功，返回评论ID",
                    content = @Content(schema = @Schema(implementation = Long.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数验证失败")
    })
    @PostMapping
    public ApiResponse<Long> createComment(
            @Parameter(description = "评论创建请求", required = true)
            @RequestBody @Valid CreateCommentRequest request) {
        Long userId = UserContext.requireUserId();
        Long commentId = commentCommandService.createComment(userId, new CreateCommentCommand(
                request.getPostId(),
                request.getContent(),
                request.getRootId(),
                request.getReplyToCommentId(),
                request.getImageIds(),
                request.getVoiceId(),
                request.getVoiceDuration()
        ));
        return ApiResponse.success(commentId);
    }

    @Operation(summary = "更新评论", description = "更新评论内容。只有评论作者可以更新自己的评论。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数验证失败"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @PutMapping("/{commentId}")
    public ApiResponse<Void> updateComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "评论更新请求", required = true)
            @RequestBody @Valid UpdateCommentRequest request) {
        Long userId = UserContext.requireUserId();
        commentCommandService.updateComment(userId, commentId, new UpdateCommentCommand(request.getContent()));
        return ApiResponse.success();
    }

    @Operation(summary = "删除评论", description = "删除评论。只有评论作者可以删除自己的评论。删除顶级评论会同时删除所有回复。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        Long userId = UserContext.requireUserId();
        commentCommandService.deleteComment(userId, UserContext.isAdmin(), commentId);
        return ApiResponse.success();
    }
}
