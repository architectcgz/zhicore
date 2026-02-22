package com.zhicore.user.interfaces.controller;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.service.UserApplicationService;
import com.zhicore.user.interfaces.dto.request.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * 用户控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "用户管理", description = "用户信息查询、资料更新、用户状态管理等接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;

    /**
     * 获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @Operation(
            summary = "获取用户详细信息",
            description = "根据用户ID获取用户的详细信息，包括个人资料、统计数据等"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = UserVO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "用户不存在"
            )
    })
    @GetMapping("/{userId}")
    public ApiResponse<UserVO> getUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        UserVO user = userApplicationService.getUserById(userId);
        return ApiResponse.success(user);
    }

    /**
     * 获取用户简要信息
     *
     * @param userId 用户ID
     * @return 用户简要信息
     */
    @Operation(
            summary = "获取用户简要信息",
            description = "获取用户的基本信息，用于列表展示等场景"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = UserSimpleDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "用户不存在"
            )
    })
    @GetMapping("/{userId}/simple")
    public ApiResponse<UserSimpleDTO> getUserSimple(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        UserSimpleDTO user = userApplicationService.getUserSimpleById(userId);
        return ApiResponse.success(user);
    }

    /**
     * 批量获取用户简要信息
     *
     * @param userIds 用户ID集合
     * @return 用户简要信息Map
     */
    @Operation(
            summary = "批量获取用户简要信息",
            description = "根据用户ID集合批量获取用户简要信息，返回Map结构"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "查询成功"
            )
    })
    @PostMapping("/batch/simple")
    public ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsers(
            @Parameter(description = "用户ID集合", required = true)
            @RequestBody Set<Long> userIds) {
        Map<Long, UserSimpleDTO> users = userApplicationService.batchGetUsersSimple(userIds);
        return ApiResponse.success(users);
    }

    /**
     * 更新用户资料
     *
     * @param userId 用户ID
     * @param request 更新请求
     * @return 操作结果
     */
    @Operation(
            summary = "更新用户资料",
            description = "更新用户的个人资料信息，如昵称、简介、头像等",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "更新成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "未授权"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "无权限修改其他用户资料"
            )
    })
    @PutMapping("/{userId}/profile")
    public ApiResponse<Void> updateProfile(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "用户资料更新信息", required = true)
            @Valid @RequestBody UpdateProfileRequest request) {
        userApplicationService.updateProfile(userId, request);
        return ApiResponse.success();
    }

    /**
     * 禁用用户
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @Operation(
            summary = "禁用用户",
            description = "禁用指定用户账号，禁用后用户无法登录系统（管理员操作）",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "禁用成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "未授权"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "无管理员权限"
            )
    })
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        userApplicationService.disableUser(userId);
        return ApiResponse.success();
    }

    /**
     * 启用用户
     *
     * @param userId 用户ID
     * @return 操作结果
     */
    @Operation(
            summary = "启用用户",
            description = "启用被禁用的用户账号（管理员操作）",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "启用成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "未授权"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "无管理员权限"
            )
    })
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        userApplicationService.enableUser(userId);
        return ApiResponse.success();
    }

    /**
     * 分配角色
     *
     * @param userId 用户ID
     * @param roleName 角色名称
     * @return 操作结果
     */
    @Operation(
            summary = "分配角色",
            description = "为用户分配指定角色（管理员操作）",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "分配成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "未授权"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "无管理员权限"
            )
    })
    @PostMapping("/{userId}/roles/{roleName}")
    public ApiResponse<Void> assignRole(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "角色名称", required = true, example = "ADMIN")
            @PathVariable String roleName) {
        userApplicationService.assignRole(userId, roleName);
        return ApiResponse.success();
    }
}
