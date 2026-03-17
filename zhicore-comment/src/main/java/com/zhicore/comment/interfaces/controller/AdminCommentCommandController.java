package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.service.command.AdminCommentCommandService;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧评论写控制器。
 */
@Tag(name = "管理员-评论写接口", description = "管理员评论删除接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
public class AdminCommentCommandController {

    private final AdminCommentCommandService adminCommentCommandService;

    @Operation(summary = "删除评论", description = "管理员删除评论（软删除）。删除顶级评论会同时删除所有回复。")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "评论ID", required = true, example = "1234567890")
            @PathVariable @Min(value = 1, message = "评论ID必须为正数") Long commentId) {
        log.info("Admin delete comment: commentId={}", commentId);
        adminCommentCommandService.deleteComment(commentId);
        return ApiResponse.success();
    }
}
