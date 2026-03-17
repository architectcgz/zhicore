package com.zhicore.admin.application.service.command;

import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.api.client.AdminPostClient;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章管理写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostManageCommandService {

    private final AdminPostClient postServiceClient;
    private final AuditLogRepository auditLogRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Transactional
    public void deletePost(Long adminId, Long postId, String reason) {
        log.info("Admin {} deleting post {} with reason: {}", adminId, postId, reason);

        ApiResponse<Void> response = postServiceClient.deletePost(postId);
        if (!response.isSuccess()) {
            throw new BusinessException("删除文章失败: " + response.getMessage());
        }

        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.DELETE_POST,
                "post",
                postId,
                reason
        );
        auditLogRepository.save(auditLog);

        log.info("Post {} deleted successfully by admin {}", postId, adminId);
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
