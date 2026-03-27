package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.api.dto.user.FollowerCursorPageDTO;
import com.zhicore.user.application.dto.FollowStatsVO;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.service.query.FollowQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 关注查询控制器。
 */
@Tag(name = "用户关注查询", description = "粉丝、关注列表和关注统计查询接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowQueryController {

    private final FollowQueryService followQueryService;

    @Operation(summary = "获取粉丝列表", description = "分页查询指定用户的粉丝列表")
    @GetMapping("/{userId}/followers")
    public ApiResponse<List<UserVO>> getFollowers(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(followQueryService.getFollowers(userId, page, size));
    }

    @Operation(summary = "按游标获取粉丝列表", description = "供广播场景按稳定游标分页拉取粉丝")
    @GetMapping("/{userId}/followers/cursor")
    public ApiResponse<FollowerCursorPageDTO> getFollowersByCursor(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "上一页最后一条关注时间", example = "2026-03-27T10:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterCreatedAt,
            @Parameter(description = "上一页最后一条粉丝ID", example = "100")
            @RequestParam(required = false) Long afterFollowerId,
            @Parameter(description = "每页大小", example = "200")
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.success(
                followQueryService.getFollowersByCursor(userId, afterCreatedAt, afterFollowerId, limit)
        );
    }

    @Operation(summary = "获取关注列表", description = "分页查询指定用户的关注列表")
    @GetMapping("/{userId}/following")
    public ApiResponse<List<UserVO>> getFollowings(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(followQueryService.getFollowings(userId, page, size));
    }

    @Operation(summary = "获取关注统计", description = "获取用户的关注统计信息，包括粉丝数、关注数等")
    @GetMapping("/{userId}/follow-stats")
    public ApiResponse<FollowStatsVO> getFollowStats(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "当前用户ID（可选）", example = "2")
            @RequestParam(required = false) @Min(value = 1, message = "当前用户ID必须为正数") Long currentUserId) {
        return ApiResponse.success(followQueryService.getFollowStats(userId, currentUserId));
    }

    @Operation(summary = "检查是否已关注", description = "检查当前用户是否已关注目标用户")
    @GetMapping("/{userId}/following/{targetUserId}/check")
    public ApiResponse<Boolean> checkFollowing(
            @Parameter(description = "当前用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "目标用户ID必须为正数") Long targetUserId) {
        return ApiResponse.success(followQueryService.isFollowing(userId, targetUserId));
    }
}
