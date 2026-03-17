package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.service.query.BlockQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 拉黑查询控制器。
 */
@Tag(name = "用户拉黑查询", description = "拉黑列表和拉黑关系查询接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class BlockQueryController {

    private final BlockQueryService blockQueryService;

    @Operation(summary = "获取拉黑列表", description = "分页查询指定用户的拉黑列表")
    @GetMapping("/{blockerId}/blocking")
    public ApiResponse<List<UserVO>> getBlockedUsers(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(blockQueryService.getBlockedUsers(blockerId, page, size));
    }

    @Operation(summary = "检查是否已拉黑", description = "检查当前用户是否已拉黑目标用户")
    @GetMapping("/{blockerId}/blocking/{blockedId}/check")
    public ApiResponse<Boolean> checkBlocked(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "被拉黑者ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "被拉黑者ID必须为正数") Long blockedId) {
        return ApiResponse.success(blockQueryService.isBlocked(blockerId, blockedId));
    }
}
