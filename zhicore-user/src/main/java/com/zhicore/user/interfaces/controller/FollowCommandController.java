package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.service.command.FollowCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 关注命令控制器。
 */
@Tag(name = "用户关注操作", description = "关注和取消关注写接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowCommandController {

    private final FollowCommandService followCommandService;

    @Operation(summary = "关注用户", description = "关注指定用户，建立关注关系")
    @PostMapping("/{userId}/following/{targetUserId}")
    public ApiResponse<Void> follow(
            @Parameter(description = "当前用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "目标用户ID必须为正数") Long targetUserId) {
        followCommandService.follow(userId, targetUserId);
        return ApiResponse.success();
    }

    @Operation(summary = "取消关注", description = "取消关注指定用户，解除关注关系")
    @DeleteMapping("/{userId}/following/{targetUserId}")
    public ApiResponse<Void> unfollow(
            @Parameter(description = "当前用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "目标用户ID必须为正数") Long targetUserId) {
        followCommandService.unfollow(userId, targetUserId);
        return ApiResponse.success();
    }
}
