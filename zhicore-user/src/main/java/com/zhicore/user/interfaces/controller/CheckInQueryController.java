package com.zhicore.user.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.service.CheckInQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

/**
 * 签到查询控制器。
 */
@Tag(name = "用户签到查询", description = "签到记录和统计查询接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class CheckInQueryController {

    private final CheckInQueryService checkInQueryService;

    @Operation(summary = "获取签到统计", description = "获取用户的签到统计信息，包括连续签到天数、总签到天数等")
    @GetMapping("/{userId}/check-in/stats")
    public ApiResponse<CheckInVO> getCheckInStats(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        return ApiResponse.success(checkInQueryService.getCheckInStats(userId));
    }

    @Operation(summary = "获取月度签到记录", description = "获取指定月份的签到记录，返回位图表示每天的签到状态")
    @GetMapping("/{userId}/check-in/monthly")
    public ApiResponse<Long> getMonthlyCheckIn(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "年份", required = true, example = "2026")
            @RequestParam int year,
            @Parameter(description = "月份", required = true, example = "2")
            @RequestParam int month) {
        return ApiResponse.success(checkInQueryService.getMonthlyCheckInBitmap(userId, YearMonth.of(year, month)));
    }
}
