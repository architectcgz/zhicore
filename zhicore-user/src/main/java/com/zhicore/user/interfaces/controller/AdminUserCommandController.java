package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.service.command.AdminUserCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户命令控制器。
 */
@Tag(name = "管理员-用户操作", description = "管理员用户状态和 Token 管理写接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserCommandController {

    private final AdminUserCommandService adminUserCommandService;

    @Operation(summary = "禁用用户", description = "管理员禁用指定用户账号，禁用后用户无法登录系统")
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        log.info("Admin disable user: userId={}", userId);
        adminUserCommandService.disableUser(userId);
        return ApiResponse.success();
    }

    @Operation(summary = "启用用户", description = "管理员启用被禁用的用户账号，恢复用户登录权限")
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        log.info("Admin enable user: userId={}", userId);
        adminUserCommandService.enableUser(userId);
        return ApiResponse.success();
    }

    @Operation(summary = "使用户所有Token失效", description = "管理员强制使指定用户的所有登录Token失效，用户需要重新登录")
    @PostMapping("/{userId}/invalidate-tokens")
    public ApiResponse<Void> invalidateUserTokens(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        log.info("Admin invalidate user tokens: userId={}", userId);
        adminUserCommandService.invalidateUserTokens(userId);
        return ApiResponse.success();
    }
}
