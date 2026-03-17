package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.service.command.UserManageCommandService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.admin.interfaces.dto.request.DisableUserRequest;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理写控制器。
 */
@Tag(name = "用户管理", description = "管理员用户写接口")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class UserManageCommandController {

    private final UserManageCommandService userManageCommandService;

    @Operation(summary = "禁用用户", description = "管理员禁用指定用户，需要提供禁用原因")
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要禁用的用户ID", required = true, example = "1001")
            @PathVariable("userId") @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "禁用用户请求信息", required = true)
            @Valid @RequestBody DisableUserRequest request) {
        userManageCommandService.disableUser(adminId, userId, request.getReason());
        return ApiResponse.success();
    }

    @Operation(summary = "启用用户", description = "管理员启用被禁用的用户")
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要启用的用户ID", required = true, example = "1001")
            @PathVariable("userId") @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        userManageCommandService.enableUser(adminId, userId);
        return ApiResponse.success();
    }
}
