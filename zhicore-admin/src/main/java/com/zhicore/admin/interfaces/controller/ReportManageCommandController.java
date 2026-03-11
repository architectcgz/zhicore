package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.service.ReportManageCommandService;
import com.zhicore.admin.domain.model.ReportHandleAction;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.admin.interfaces.dto.request.HandleReportRequest;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报管理写控制器。
 */
@Tag(name = "举报管理", description = "管理员举报写接口")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class ReportManageCommandController {

    private final ReportManageCommandService reportManageCommandService;

    @Operation(summary = "处理举报", description = "管理员处理举报，可以批准或拒绝举报")
    @PostMapping("/{reportId}/handle")
    public ApiResponse<Void> handleReport(
            @Parameter(description = "管理员用户ID", required = true)
            @RequestHeader("X-User-Id") Long adminId,
            @Parameter(description = "举报ID", required = true, example = "4001")
            @PathVariable("reportId") @Min(value = 1, message = "举报ID必须为正数") Long reportId,
            @Parameter(description = "处理举报请求信息", required = true)
            @Valid @RequestBody HandleReportRequest request) {
        ReportHandleAction action = ReportHandleAction.valueOf(request.getAction().toUpperCase());
        reportManageCommandService.handleReport(adminId, reportId, action, request.getRemark());
        return ApiResponse.success();
    }
}
