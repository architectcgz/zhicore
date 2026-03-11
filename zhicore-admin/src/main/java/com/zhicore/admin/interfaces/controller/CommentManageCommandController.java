package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.service.CommentManageCommandService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.admin.interfaces.dto.request.DeleteContentRequest;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论管理写控制器。
 */
@Tag(name = "评论管理", description = "管理员评论写接口")
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class CommentManageCommandController {

    private final CommentManageCommandService commentManageCommandService;

    @Operation(summary = "删除评论", description = "管理员删除指定评论，需要提供删除原因")
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要删除的评论ID", required = true, example = "3001")
            @PathVariable("commentId") @Min(value = 1, message = "评论ID必须为正数") Long commentId,
            @Parameter(description = "删除评论请求信息", required = true)
            @Valid @RequestBody DeleteContentRequest request) {
        commentManageCommandService.deleteComment(adminId, commentId, request.getReason());
        return ApiResponse.success();
    }
}
