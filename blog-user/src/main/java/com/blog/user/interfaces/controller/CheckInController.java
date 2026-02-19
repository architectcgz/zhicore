package com.blog.user.interfaces.controller;

import com.blog.common.result.ApiResponse;
import com.blog.user.application.dto.CheckInVO;
import com.blog.user.application.service.CheckInApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

/**
 * 签到控制器
 *
 * @author Blog Team
 */
@Tag(name = "用户签到管理", description = "用户签到功能，包括每日签到、查询签到记录、签到统计、签到奖励等功能")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInApplicationService checkInApplicationService;

    /**
     * 用户签到
     *
     * @param userId 用户ID
     * @return 签到结果
     */
    @Operation(
            summary = "用户签到",
            description = "用户每日签到，记录签到信息并返回签到结果"
    )
    @PostMapping("/{userId}/check-in")
    public ApiResponse<CheckInVO> checkIn(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        CheckInVO result = checkInApplicationService.checkIn(userId);
        return ApiResponse.success(result);
    }

    /**
     * 获取签到统计
     *
     * @param userId 用户ID
     * @return 签到统计
     */
    @Operation(
            summary = "获取签到统计",
            description = "获取用户的签到统计信息，包括连续签到天数、总签到天数等"
    )
    @GetMapping("/{userId}/check-in/stats")
    public ApiResponse<CheckInVO> getCheckInStats(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        CheckInVO stats = checkInApplicationService.getCheckInStats(userId);
        return ApiResponse.success(stats);
    }

    /**
     * 获取月度签到记录
     *
     * @param userId 用户ID
     * @param year 年份
     * @param month 月份
     * @return 签到位图
     */
    @Operation(
            summary = "获取月度签到记录",
            description = "获取指定月份的签到记录，返回位图表示每天的签到状态"
    )
    @GetMapping("/{userId}/check-in/monthly")
    public ApiResponse<Long> getMonthlyCheckIn(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId,
            @Parameter(description = "年份", required = true, example = "2026")
            @RequestParam int year,
            @Parameter(description = "月份", required = true, example = "2")
            @RequestParam int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        long bitmap = checkInApplicationService.getMonthlyCheckInBitmap(userId, yearMonth);
        return ApiResponse.success(bitmap);
    }
}
