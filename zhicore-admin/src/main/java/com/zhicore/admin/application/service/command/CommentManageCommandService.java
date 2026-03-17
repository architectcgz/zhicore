package com.zhicore.admin.application.service.command;

import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.api.client.AdminCommentClient;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评论管理写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentManageCommandService {

    private final AdminCommentClient commentServiceClient;
    private final AuditLogRepository auditLogRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Transactional
    public void deleteComment(Long adminId, Long commentId, String reason) {
        log.info("Admin {} deleting comment {} with reason: {}", adminId, commentId, reason);

        ApiResponse<Void> response = commentServiceClient.deleteComment(commentId);
        if (!response.isSuccess()) {
            throw new BusinessException("删除评论失败: " + response.getMessage());
        }

        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.DELETE_COMMENT,
                "comment",
                commentId,
                reason
        );
        auditLogRepository.save(auditLog);

        log.info("Comment {} deleted successfully by admin {}", commentId, adminId);
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess()) {
            log.error("生成ID失败: code={}, message={}", response.getCode(), response.getMessage());
            throw new BusinessException("生成ID失败: " + response.getMessage());
        }

        Long id = response.getData();
        if (id == null || id <= 0) {
            log.error("生成的ID无效: {}", id);
            throw new BusinessException("生成的ID无效");
        }
        return id;
    }
}
