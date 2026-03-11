package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.service.BlockCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 拉黑命令控制器。
 */
@Tag(name = "用户拉黑操作", description = "拉黑和取消拉黑写接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class BlockCommandController {

    private final BlockCommandService blockCommandService;

    @Operation(summary = "拉黑用户", description = "拉黑指定用户，建立拉黑关系，并自动取消双向关注关系")
    @PostMapping("/{blockerId}/blocking/{blockedId}")
    public ApiResponse<Void> block(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "被拉黑者ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "被拉黑者ID必须为正数") Long blockedId) {
        blockCommandService.block(blockerId, blockedId);
        return ApiResponse.success();
    }

    @Operation(summary = "取消拉黑", description = "取消拉黑指定用户，解除拉黑关系")
    @DeleteMapping("/{blockerId}/blocking/{blockedId}")
    public ApiResponse<Void> unblock(
            @Parameter(description = "拉黑者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "拉黑者ID必须为正数") Long blockerId,
            @Parameter(description = "被拉黑者ID", required = true, example = "2")
            @PathVariable @Min(value = 1, message = "被拉黑者ID必须为正数") Long blockedId) {
        blockCommandService.unblock(blockerId, blockedId);
        return ApiResponse.success();
    }
}
