package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.UserManageVO;
import com.zhicore.admin.application.service.UserManageService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.admin.interfaces.dto.request.DisableUserRequest;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "管理员用户管理相关接口，包括查询用户列表、禁用/启用用户等功能")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class UserManageController {
    
    private final UserManageService userManageService;
    
    /**
     * 查询用户列表
     */
    @Operation(
            summary = "查询用户列表",
            description = "分页查询用户列表，支持按关键词和状态筛选"
    )
    @GetMapping
    public ApiResponse<PageResult<UserManageVO>> listUsers(
            @Parameter(description = "搜索关键词（用户名或邮箱）", example = "zhangsan")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "用户状态（ACTIVE/DISABLED）", example = "ACTIVE")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageResult<UserManageVO> result = userManageService.listUsers(keyword, status, page, size);
        return ApiResponse.success(result);
    }
    
    /**
     * 禁用用户
     */
    @Operation(
            summary = "禁用用户",
            description = "管理员禁用指定用户，需要提供禁用原因"
    )
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要禁用的用户ID", required = true, example = "1001")
            @PathVariable("userId") @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "禁用用户请求信息", required = true)
            @Valid @RequestBody DisableUserRequest request) {
        userManageService.disableUser(adminId, userId, request.getReason());
        return ApiResponse.success();
    }
    
    /**
     * 启用用户
     */
    @Operation(
            summary = "启用用户",
            description = "管理员启用被禁用的用户"
    )
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "要启用的用户ID", required = true, example = "1001")
            @PathVariable("userId") @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        userManageService.enableUser(adminId, userId);
        return ApiResponse.success();
    }
}
