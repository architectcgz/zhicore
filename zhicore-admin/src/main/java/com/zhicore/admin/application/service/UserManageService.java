package com.zhicore.admin.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.admin.application.dto.UserManageVO;
import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.admin.infrastructure.feign.AdminUserServiceClient;
import com.zhicore.admin.infrastructure.sentinel.AdminSentinelHandlers;
import com.zhicore.admin.infrastructure.sentinel.AdminSentinelResources;
import com.zhicore.admin.infrastructure.feign.IdGeneratorClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageService {
    
    private final AdminUserServiceClient userServiceClient;
    private final AuditLogRepository auditLogRepository;
    private final IdGeneratorClient idGeneratorClient;
    
    /**
     * 查询用户列表
     *
     * @param keyword 关键词（用户名/邮箱/昵称）
     * @param status  状态
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    @SentinelResource(
            value = AdminSentinelResources.LIST_USERS,
            blockHandlerClass = AdminSentinelHandlers.class,
            blockHandler = "handleListUsersBlocked"
    )
    public PageResult<UserManageVO> listUsers(String keyword, String status, int page, int size) {
        ApiResponse<PageResult<AdminUserServiceClient.UserManageDTO>> response = 
                userServiceClient.queryUsers(keyword, status, page, size);
        
        if (!response.isSuccess()) {
            throw new BusinessException(response.getMessage());
        }
        
        PageResult<AdminUserServiceClient.UserManageDTO> result = response.getData();
        List<UserManageVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), voList);
    }
    
    /**
     * 禁用用户
     *
     * @param adminId 管理员ID
     * @param userId  用户ID
     * @param reason  禁用原因
     */
    @Transactional
    public void disableUser(Long adminId, Long userId, String reason) {
        log.info("Admin {} disabling user {} with reason: {}", adminId, userId, reason);
        
        // 1. 禁用用户
        ApiResponse<Void> disableResponse = userServiceClient.disableUser(userId);
        if (!disableResponse.isSuccess()) {
            throw new BusinessException("禁用用户失败: " + disableResponse.getMessage());
        }
        
        // 2. 使用户所有 Token 失效
        ApiResponse<Void> invalidateResponse = userServiceClient.invalidateUserTokens(userId);
        if (!invalidateResponse.isSuccess()) {
            log.warn("Failed to invalidate tokens for user {}: {}", userId, invalidateResponse.getMessage());
        }
        
        // 3. 记录审计日志
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
    
    /**
     * 启用用户
     *
     * @param adminId 管理员ID
     * @param userId  用户ID
     */
    @Transactional
    public void enableUser(Long adminId, Long userId) {
        log.info("Admin {} enabling user {}", adminId, userId);
        
        // 1. 启用用户
        ApiResponse<Void> response = userServiceClient.enableUser(userId);
        if (!response.isSuccess()) {
            throw new BusinessException("启用用户失败: " + response.getMessage());
        }
        
        // 2. 记录审计日志
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
        ApiResponse<Long> response = idGeneratorClient.generateSnowflakeId();
        if (!response.isSuccess()) {
            throw new BusinessException("生成ID失败: " + response.getMessage());
        }
        return response.getData();
    }
    
    private UserManageVO toVO(AdminUserServiceClient.UserManageDTO dto) {
        return UserManageVO.builder()
                .id(dto.id())
                .username(dto.username())
                .email(dto.email())
                .nickname(dto.nickname())
                .avatar(dto.avatar())
                .status(dto.status())
                .createdAt(dto.createdAt())
                .roles(dto.roles())
                .build();
    }
}
