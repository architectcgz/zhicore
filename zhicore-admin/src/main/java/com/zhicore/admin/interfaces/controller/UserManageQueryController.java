package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.UserManageVO;
import com.zhicore.admin.application.service.query.UserManageQueryService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理查询控制器。
 */
@Tag(name = "用户管理", description = "管理员用户查询接口")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class UserManageQueryController {

    private final UserManageQueryService userManageQueryService;

    @Operation(summary = "查询用户列表", description = "分页查询用户列表，支持按关键词和状态筛选")
    @GetMapping
    public ApiResponse<PageResult<UserManageVO>> listUsers(
            @Parameter(description = "搜索关键词（用户名或邮箱）", example = "zhangsan")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "用户状态（ACTIVE/DISABLED）", example = "ACTIVE")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        return ApiResponse.success(userManageQueryService.listUsers(keyword, status, page, size));
    }
}
