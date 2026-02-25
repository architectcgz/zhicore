package com.zhicore.admin.application.service;

import com.zhicore.admin.application.dto.ReportVO;
import com.zhicore.admin.domain.model.*;
import com.zhicore.admin.domain.model.*;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.admin.domain.repository.ReportRepository;
import com.zhicore.admin.infrastructure.feign.IdGeneratorClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 举报管理应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportManageService {
    
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final PostManageService postManageService;
    private final CommentManageService commentManageService;
    private final UserManageService userManageService;
    private final IdGeneratorClient idGeneratorClient;
    
    /**
     * 查询待处理举报列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResult<ReportVO> listPendingReports(int page, int size) {
        PageResult<Report> result = reportRepository.findPendingReports(page, size);
        
        List<ReportVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), voList);
    }
    
    /**
     * 按状态查询举报列表
     *
     * @param status 状态
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    public PageResult<ReportVO> listReportsByStatus(String status, int page, int size) {
        ReportStatus reportStatus = ReportStatus.valueOf(status.toUpperCase());
        PageResult<Report> result = reportRepository.findByStatus(reportStatus, page, size);
        
        List<ReportVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), voList);
    }
    
    /**
     * 处理举报
     *
     * @param adminId  管理员ID
     * @param reportId 举报ID
     * @param action   处理动作
     * @param remark   处理备注
     */
    @Transactional
    public void handleReport(Long adminId, Long reportId, ReportHandleAction action, String remark) {
        log.info("Admin {} handling report {} with action: {}", adminId, reportId, action);
        
        // 1. 查询举报
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("举报不存在"));
        
        if (!report.isPending()) {
            throw new BusinessException("举报已处理，不能重复处理");
        }
        
        // 2. 执行处罚动作
        executeAction(adminId, report, action, remark);
        
        // 3. 更新举报状态
        report.handle(adminId, action, remark);
        reportRepository.update(report);
        
        // 4. 记录审计日志
        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.HANDLE_REPORT,
                "report",
                reportId,
                String.format("处理动作: %s, 备注: %s", action.name(), remark)
        );
        auditLogRepository.save(auditLog);
        
        log.info("Report {} handled successfully by admin {} with action {}", reportId, adminId, action);
    }
    
    /**
     * 执行处罚动作
     */
    private void executeAction(Long adminId, Report report, ReportHandleAction action, String remark) {
        switch (action) {
            case DELETE_CONTENT -> {
                if ("post".equals(report.getTargetType())) {
                    postManageService.deletePost(adminId, report.getTargetId(), remark);
                } else if ("comment".equals(report.getTargetType())) {
                    commentManageService.deleteComment(adminId, report.getTargetId(), remark);
                }
            }
            case WARN_USER -> {
                // 发送警告通知（可以通过消息队列发送通知）
                log.info("Warning user {} for report {}", report.getReportedUserId(), report.getId());
            }
            case BAN_USER -> {
                userManageService.disableUser(adminId, report.getReportedUserId(), remark);
            }
            case IGNORE -> {
                // 忽略举报，不执行任何动作
                log.info("Ignoring report {}", report.getId());
            }
        }
    }
    
    private Long generateId() {
        ApiResponse<Long> response = idGeneratorClient.generateSnowflakeId();
        if (!response.isSuccess()) {
            throw new BusinessException("生成ID失败: " + response.getMessage());
        }
        return response.getData();
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
