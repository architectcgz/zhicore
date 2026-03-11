package com.zhicore.admin.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.admin.application.dto.ReportVO;
import com.zhicore.admin.application.sentinel.AdminSentinelHandlers;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import com.zhicore.admin.domain.model.Report;
import com.zhicore.admin.domain.model.ReportStatus;
import com.zhicore.admin.domain.repository.ReportRepository;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 举报管理查询服务。
 */
@Service
@RequiredArgsConstructor
public class ReportManageQueryService {

    private final ReportRepository reportRepository;

    @SentinelResource(
            value = AdminSentinelResources.LIST_PENDING_REPORTS,
            blockHandlerClass = AdminSentinelHandlers.class,
            blockHandler = "handleListPendingReportsBlocked"
    )
    public PageResult<ReportVO> listPendingReports(int page, int size) {
        PageResult<Report> result = reportRepository.findPendingReports(page, size);
        List<ReportVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(page, size, result.getTotal(), voList);
    }

    @SentinelResource(
            value = AdminSentinelResources.LIST_REPORTS_BY_STATUS,
            blockHandlerClass = AdminSentinelHandlers.class,
            blockHandler = "handleListReportsByStatusBlocked"
    )
    public PageResult<ReportVO> listReportsByStatus(String status, int page, int size) {
        ReportStatus reportStatus = ReportStatus.valueOf(status.toUpperCase());
        PageResult<Report> result = reportRepository.findByStatus(reportStatus, page, size);
        List<ReportVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(page, size, result.getTotal(), voList);
    }

    private ReportVO toVO(Report report) {
        return ReportVO.builder()
                .id(report.getId())
                .reporterId(report.getReporterId())
                .reportedUserId(report.getReportedUserId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .status(report.getStatus().name())
                .handlerId(report.getHandlerId())
                .handleAction(report.getHandleAction() != null ? report.getHandleAction().name() : null)
                .handleRemark(report.getHandleRemark())
                .handledAt(report.getHandledAt())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
