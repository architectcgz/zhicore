package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.ReportVO;
import com.zhicore.admin.application.service.ReportManageService;
import com.zhicore.admin.domain.model.ReportHandleAction;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.admin.interfaces.dto.request.HandleReportRequest;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 举报管理控制器
 */
@Tag(name = "举报管理", description = "管理员举报管理相关接口，包括查询举报列表、处理举报等功能")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class ReportManageController {
    
    private final ReportManageService reportManageService;
    
    /**
     * 查询待处理举报列表
     */
    @Operation(
            summary = "查询待处理举报列表",
            description = "分页查询待处理的举报列表"
    )
    @GetMapping("/pending")
    public ApiResponse<PageResult<ReportVO>> listPendingReports(
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageResult<ReportVO> result = reportManageService.listPendingReports(page, size);
        return ApiResponse.success(result);
    }
    
    /**
     * 按状态查询举报列表
     */
    @Operation(
            summary = "按状态查询举报列表",
            description = "分页查询指定状态的举报列表"
    )
    @GetMapping
    public ApiResponse<PageResult<ReportVO>> listReports(
            @Parameter(description = "举报状态（PENDING/APPROVED/REJECTED）", example = "PENDING")
            @RequestParam(value = "status", defaultValue = "PENDING") String status,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageResult<ReportVO> result = reportManageService.listReportsByStatus(status, page, size);
        return ApiResponse.success(result);
    }
    
    /**
     * 处理举报
     */
    @Operation(
            summary = "处理举报",
            description = "管理员处理举报，可以批准或拒绝举报"
    )
    @PostMapping("/{reportId}/handle")
    public ApiResponse<Void> handleReport(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "举报ID", required = true, example = "4001")
            @PathVariable("reportId") @Min(value = 1, message = "举报ID必须为正数") Long reportId,
            @Parameter(description = "处理举报请求信息", required = true)
            @Valid @RequestBody HandleReportRequest request) {
        ReportHandleAction action = ReportHandleAction.valueOf(request.getAction().toUpperCase());
        reportManageService.handleReport(adminId, reportId, action, request.getRemark());
        return ApiResponse.success();
    }
}
