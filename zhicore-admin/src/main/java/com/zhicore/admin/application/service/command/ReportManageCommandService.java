package com.zhicore.admin.application.service.command;

import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.model.Report;
import com.zhicore.admin.domain.model.ReportHandleAction;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.admin.domain.repository.ReportRepository;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报管理写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportManageCommandService {

    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final PostManageCommandService postManageCommandService;
    private final CommentManageCommandService commentManageCommandService;
    private final UserManageCommandService userManageCommandService;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Transactional
    public void handleReport(Long adminId, Long reportId, ReportHandleAction action, String remark) {
        log.info("Admin {} handling report {} with action: {}", adminId, reportId, action);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("举报不存在"));
        if (!report.isPending()) {
            throw new BusinessException("举报已处理，不能重复处理");
        }

        executeAction(adminId, report, action, remark);

        report.handle(adminId, action, remark);
        reportRepository.update(report);

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

    private void executeAction(Long adminId, Report report, ReportHandleAction action, String remark) {
        switch (action) {
            case DELETE_CONTENT -> {
                if ("post".equals(report.getTargetType())) {
                    postManageCommandService.deletePost(adminId, report.getTargetId(), remark);
                } else if ("comment".equals(report.getTargetType())) {
                    commentManageCommandService.deleteComment(adminId, report.getTargetId(), remark);
                }
            }
            case WARN_USER -> log.info("Warning user {} for report {}", report.getReportedUserId(), report.getId());
            case BAN_USER -> userManageCommandService.disableUser(adminId, report.getReportedUserId(), remark);
            case IGNORE -> log.info("Ignoring report {}", report.getId());
        }
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess()) {
            throw new BusinessException("生成ID失败: " + response.getMessage());
        }
        return response.getData();
    }
}
