package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.dto.FollowStatsVO;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.service.FollowApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 关注控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "用户关注管理", description = "用户关注功能，包括关注用户、取消关注、查询粉丝列表、查询关注列表、关注统计等功能")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowApplicationService followApplicationService;

    /**
     * 关注用户
     *
     * @param userId 当前用户ID
     * @param targetUserId 目标用户ID
     * @return 操作结果
     */
    @Operation(
            summary = "关注用户",
            description = "关注指定用户，建立关注关系"
    )
    @PostMapping("/{userId}/following/{targetUserId}")
    public ApiResponse<Void> follow(
            @Parameter(description = "当前用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "目标用户ID必须为正数") Long targetUserId) {
        followApplicationService.follow(userId, targetUserId);
        return ApiResponse.success();
    }

    /**
     * 取消关注
     *
     * @param userId 当前用户ID
     * @param targetUserId 目标用户ID
     * @return 操作结果
     */
    @Operation(
            summary = "取消关注",
            description = "取消关注指定用户，解除关注关系"
    )
    @DeleteMapping("/{userId}/following/{targetUserId}")
    public ApiResponse<Void> unfollow(
            @Parameter(description = "当前用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "目标用户ID必须为正数") Long targetUserId) {
        followApplicationService.unfollow(userId, targetUserId);
        return ApiResponse.success();
    }

    /**
     * 获取粉丝列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 粉丝列表
     */
    @Operation(
            summary = "获取粉丝列表",
            description = "分页查询指定用户的粉丝列表"
    )
    @GetMapping("/{userId}/followers")
    public ApiResponse<List<UserVO>> getFollowers(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        List<UserVO> followers = followApplicationService.getFollowers(userId, page, size);
        return ApiResponse.success(followers);
    }

    /**
     * 获取关注列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 关注列表
     */
    @Operation(
            summary = "获取关注列表",
            description = "分页查询指定用户的关注列表"
    )
    @GetMapping("/{userId}/following")
    public ApiResponse<List<UserVO>> getFollowings(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        List<UserVO> followings = followApplicationService.getFollowings(userId, page, size);
        return ApiResponse.success(followings);
    }

    /**
     * 获取关注统计
     *
     * @param userId 用户ID
     * @param currentUserId 当前用户ID（可选）
     * @return 关注统计
     */
    @Operation(
            summary = "获取关注统计",
            description = "获取用户的关注统计信息，包括粉丝数、关注数等"
    )
    @GetMapping("/{userId}/follow-stats")
    public ApiResponse<FollowStatsVO> getFollowStats(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "当前用户ID（可选）", example = "2")
            @RequestParam(required = false) @Min(value = 1, message = "当前用户ID必须为正数") Long currentUserId) {
        FollowStatsVO stats = followApplicationService.getFollowStats(userId, currentUserId);
        return ApiResponse.success(stats);
    }

    /**
     * 检查是否已关注
     *
     * @param userId 当前用户ID
     * @param targetUserId 目标用户ID
     * @return 是否已关注
     */
    @Operation(
            summary = "检查是否已关注",
            description = "检查当前用户是否已关注目标用户"
    )
    @GetMapping("/{userId}/following/{targetUserId}/check")
    public ApiResponse<Boolean> checkFollowing(
            @Parameter(description = "当前用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "目标用户ID必须为正数") Long targetUserId) {
        boolean isFollowing = followApplicationService.isFollowing(userId, targetUserId);
        return ApiResponse.success(isFollowing);
    }
}
