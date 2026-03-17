package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.service.command.PostManageCommandService;
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
 * 文章管理写控制器。
 */
@Tag(name = "文章管理", description = "管理员文章写接口")
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class PostManageCommandController {

    private final PostManageCommandService postManageCommandService;

    @Operation(summary = "删除文章", description = "管理员删除指定文章，需要提供删除原因")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要删除的文章ID", required = true, example = "2001")
            @PathVariable("postId") @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "删除文章请求信息", required = true)
            @Valid @RequestBody DeleteContentRequest request) {
        postManageCommandService.deletePost(adminId, postId, request.getReason());
        return ApiResponse.success();
    }
}
