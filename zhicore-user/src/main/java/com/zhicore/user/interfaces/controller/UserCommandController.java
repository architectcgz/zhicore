package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.command.UpdateProfileCommand;
import com.zhicore.user.application.service.UserCommandService;
import com.zhicore.user.interfaces.dto.request.UpdateProfileRequest;
import com.zhicore.user.interfaces.dto.request.UpdateStrangerMessageSettingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户命令控制器。
 *
 * 负责用户写接口，不承载任何查询逻辑。
 */
@Tag(name = "用户操作", description = "用户资料、状态、角色等写接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserCommandController {

    private final UserCommandService userCommandService;

    /**
     * 更新用户资料。
     *
     * @param userId 用户 ID
     * @param request 更新请求
     * @return 操作结果
     */
    @Operation(
            summary = "更新用户资料",
            description = "更新用户的个人资料信息，如昵称、简介、头像等",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数验证失败"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限修改其他用户资料")
    })
    @PutMapping("/{userId}/profile")
    public ApiResponse<Void> updateProfile(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "用户资料更新信息", required = true)
            @Valid @RequestBody UpdateProfileRequest request) {
        userCommandService.updateProfile(userId, new UpdateProfileCommand(
                request.getNickName(),
                request.getAvatarId(),
                request.getBio()
        ));
        return ApiResponse.success();
    }

    /**
     * 更新用户是否允许陌生人消息。
     *
     * @param userId 用户 ID
     * @param request 更新请求
     * @return 操作结果
     */
    @Operation(
            summary = "更新陌生人消息设置",
            description = "更新指定用户是否允许陌生人发送私信",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @PutMapping("/{userId}/settings/stranger-message")
    public ApiResponse<Void> updateStrangerMessageSetting(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "陌生人消息设置", required = true)
            @Valid @RequestBody UpdateStrangerMessageSettingRequest request) {
        userCommandService.updateStrangerMessageSetting(userId, request.getAllowStrangerMessage());
        return ApiResponse.success();
    }

    /**
     * 禁用用户。
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    @Operation(
            summary = "禁用用户",
            description = "禁用指定用户账号，禁用后用户无法登录系统（管理员操作）",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "禁用成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    @PostMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        userCommandService.disableUser(userId);
        return ApiResponse.success();
    }

    /**
     * 启用用户。
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    @Operation(
            summary = "启用用户",
            description = "启用被禁用的用户账号（管理员操作）",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "启用成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    @PostMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        userCommandService.enableUser(userId);
        return ApiResponse.success();
    }

    /**
     * 分配角色。
     *
     * @param userId 用户 ID
     * @param roleName 角色名称
     * @return 操作结果
     */
    @Operation(
            summary = "分配角色",
            description = "为用户分配指定角色（管理员操作）",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "分配成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    @PostMapping("/{userId}/roles/{roleName}")
    public ApiResponse<Void> assignRole(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "角色名称", required = true, example = "ADMIN")
            @PathVariable String roleName) {
        userCommandService.assignRole(userId, roleName);
        return ApiResponse.success();
    }
}
