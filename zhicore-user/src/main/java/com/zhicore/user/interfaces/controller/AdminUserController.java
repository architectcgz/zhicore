package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.user.application.service.AdminUserApplicationService;
import com.zhicore.user.interfaces.dto.response.UserManageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "管理员-用户管理", description = "管理员用户管理功能，包括用户查询、用户状态管理、用户权限管理等功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserApplicationService adminUserApplicationService;

    /**
     * 查询用户列表
     */
    @Operation(
            summary = "查询用户列表",
            description = "管理员分页查询用户列表，支持关键词搜索和状态筛选"
    )
    @GetMapping
    public ApiResponse<PageResult<UserManageDTO>> queryUsers(
            @Parameter(description = "搜索关键词（用户名、邮箱等）", example = "张三")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "用户状态（NORMAL/FORBIDDEN）", example = "NORMAL")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        log.info("Admin query users: keyword={}, status={}, page={}, size={}", keyword, status, page, size);
        
        PageResult<UserManageDTO> result = adminUserApplicationService.queryUsers(keyword, status, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 禁用用户
     */
    @Operation(
            summary = "禁用用户",
            description = "管理员禁用指定用户账号，禁用后用户无法登录系统"
    )
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        log.info("Admin disable user: userId={}", userId);
        
        adminUserApplicationService.disableUser(userId);
        return ApiResponse.success();
    }

    /**
     * 启用用户
     */
    @Operation(
            summary = "启用用户",
            description = "管理员启用被禁用的用户账号，恢复用户登录权限"
    )
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        log.info("Admin enable user: userId={}", userId);
        
        adminUserApplicationService.enableUser(userId);
        return ApiResponse.success();
    }

    /**
     * 使用户所有 Token 失效
     */
    @Operation(
            summary = "使用户所有Token失效",
            description = "管理员强制使指定用户的所有登录Token失效，用户需要重新登录"
    )
    @PostMapping("/{userId}/invalidate-tokens")
    public ApiResponse<Void> invalidateUserTokens(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        log.info("Admin invalidate user tokens: userId={}", userId);
        
        adminUserApplicationService.invalidateUserTokens(userId);
        return ApiResponse.success();
    }
}
