package com.zhicore.admin.application.service.command;

import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.api.client.AdminUserClient;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageCommandService {

    private final AdminUserClient userServiceClient;
    private final AuditLogRepository auditLogRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;

    @Transactional
    public void disableUser(Long adminId, Long userId, String reason) {
        log.info("Admin {} disabling user {} with reason: {}", adminId, userId, reason);

        ApiResponse<Void> disableResponse = userServiceClient.disableUser(userId);
        if (!disableResponse.isSuccess()) {
            throw new BusinessException("禁用用户失败: " + disableResponse.getMessage());
        }

        ApiResponse<Void> invalidateResponse = userServiceClient.invalidateUserTokens(userId);
        if (!invalidateResponse.isSuccess()) {
            log.warn("Failed to invalidate tokens for user {}: {}", userId, invalidateResponse.getMessage());
        }

        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.DISABLE_USER,
                "user",
                userId,
                reason
        );
        auditLogRepository.save(auditLog);

        log.info("User {} disabled successfully by admin {}", userId, adminId);
    }

    @Transactional
    public void enableUser(Long adminId, Long userId) {
        log.info("Admin {} enabling user {}", adminId, userId);

        ApiResponse<Void> response = userServiceClient.enableUser(userId);
        if (!response.isSuccess()) {
            throw new BusinessException("启用用户失败: " + response.getMessage());
        }

        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.ENABLE_USER,
                "user",
                userId,
                null
        );
        auditLogRepository.save(auditLog);

        log.info("User {} enabled successfully by admin {}", userId, adminId);
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess()) {
            throw new BusinessException("生成ID失败: " + response.getMessage());
        }
        return response.getData();
    }
}
