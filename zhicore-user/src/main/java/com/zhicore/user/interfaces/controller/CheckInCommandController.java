package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.service.CheckInCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 签到命令控制器。
 */
@Tag(name = "用户签到操作", description = "用户签到写接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class CheckInCommandController {

    private final CheckInCommandService checkInCommandService;

    @Operation(summary = "用户签到", description = "用户每日签到，记录签到信息并返回签到结果")
    @PostMapping("/{userId}/check-in")
    public ApiResponse<CheckInVO> checkIn(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(checkInCommandService.checkIn(userId));
    }
}
