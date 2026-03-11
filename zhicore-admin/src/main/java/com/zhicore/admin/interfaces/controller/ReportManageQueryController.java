package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.ReportVO;
import com.zhicore.admin.application.service.ReportManageQueryService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报管理查询控制器。
 */
@Tag(name = "举报管理", description = "管理员举报查询接口")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class ReportManageQueryController {

    private final ReportManageQueryService reportManageQueryService;

    @Operation(summary = "查询待处理举报列表", description = "分页查询待处理的举报列表")
    @GetMapping("/pending")
    public ApiResponse<PageResult<ReportVO>> listPendingReports(
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        return ApiResponse.success(reportManageQueryService.listPendingReports(page, size));
    }

    @Operation(summary = "按状态查询举报列表", description = "分页查询指定状态的举报列表")
    @GetMapping
    public ApiResponse<PageResult<ReportVO>> listReports(
            @Parameter(description = "举报状态（PENDING/APPROVED/REJECTED）", example = "PENDING")
            @RequestParam(value = "status", defaultValue = "PENDING") String status,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        return ApiResponse.success(reportManageQueryService.listReportsByStatus(status, page, size));
    }
}
