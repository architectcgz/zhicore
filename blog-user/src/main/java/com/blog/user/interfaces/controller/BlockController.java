package com.blog.user.interfaces.controller;

import com.blog.common.result.ApiResponse;
import com.blog.user.application.dto.UserVO;
import com.blog.user.application.service.BlockApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 拉黑控制器
 *
 * @author Blog Team
 */
@Tag(name = "用户拉黑管理", description = "用户拉黑功能，包括拉黑用户、取消拉黑、查询拉黑列表等功能")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockApplicationService blockApplicationService;

    /**
     * 拉黑用户
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     * @return 操作结果
     */
    @Operation(
            summary = "拉黑用户",
            description = "拉黑指定用户，建立拉黑关系，并自动取消双向关注关系"
    )
    @PostMapping("/{blockerId}/blocking/{blockedId}")
    public ApiResponse<Void> block(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "被拉黑者ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "被拉黑者ID必须为正数") Long blockedId) {
        blockApplicationService.block(blockerId, blockedId);
        return ApiResponse.success();
    }

    /**
     * 取消拉黑
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     * @return 操作结果
     */
    @Operation(
            summary = "取消拉黑",
            description = "取消拉黑指定用户，解除拉黑关系"
    )
    @DeleteMapping("/{blockerId}/blocking/{blockedId}")
    public ApiResponse<Void> unblock(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "被拉黑者ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "被拉黑者ID必须为正数") Long blockedId) {
        blockApplicationService.unblock(blockerId, blockedId);
        return ApiResponse.success();
    }

    /**
     * 获取拉黑列表
     *
     * @param blockerId 拉黑者ID
     * @param page 页码
     * @param size 每页大小
     * @return 拉黑用户列表
     */
    @Operation(
            summary = "获取拉黑列表",
            description = "分页查询指定用户的拉黑列表"
    )
    @GetMapping("/{blockerId}/blocking")
    public ApiResponse<List<UserVO>> getBlockedUsers(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        List<UserVO> blockedUsers = blockApplicationService.getBlockedUsers(blockerId, page, size);
        return ApiResponse.success(blockedUsers);
    }

    /**
     * 检查是否已拉黑
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     * @return 是否已拉黑
     */
    @Operation(
            summary = "检查是否已拉黑",
            description = "检查当前用户是否已拉黑目标用户"
    )
    @GetMapping("/{blockerId}/blocking/{blockedId}/check")
    public ApiResponse<Boolean> checkBlocked(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "被拉黑者ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "被拉黑者ID必须为正数") Long blockedId) {
        boolean isBlocked = blockApplicationService.isBlocked(blockerId, blockedId);
        return ApiResponse.success(isBlocked);
    }
}
