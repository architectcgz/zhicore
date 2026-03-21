package com.zhicore.user.interfaces.controller;

import com.zhicore.api.dto.admin.UserManageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.query.view.UserManageView;
import com.zhicore.user.application.service.query.UserManageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * 管理员用户查询控制器。
 */
@Tag(name = "管理员-用户查询", description = "管理员用户列表查询接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserQueryController {

    private final UserManageQueryService userManageQueryService;

    @Operation(summary = "查询用户列表", description = "管理员分页查询用户列表，支持关键词搜索和状态筛选")
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
        log.debug("Admin query users: keyword={}, status={}, page={}, size={}", keyword, status, page, size);
        PageResult<UserManageView> result = userManageQueryService.queryUsers(keyword, status, page, size);
        return ApiResponse.success(toManageDtoPage(result));
    }

    private PageResult<UserManageDTO> toManageDtoPage(PageResult<UserManageView> result) {
        return PageResult.of(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(UserAssembler::toManageDTO)
                        .collect(Collectors.toList())
        );
    }
}
