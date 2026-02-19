package com.blog.admin.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计日志领域模型测试
 */
@DisplayName("AuditLog 领域模型测试")
class AuditLogTest {
    
    @Test
    @DisplayName("创建审计日志 - 成功")
    void create_shouldCreateAuditLog_whenValidParameters() {
        // Given
        Long id = 1L;
        Long operatorId = 789L;
        AuditAction action = AuditAction.DISABLE_USER;
        String targetType = "user";
        Long targetId = 123L;
        String reason = "违规操作";
        
        // When
        AuditLog auditLog = AuditLog.create(id, operatorId, action, targetType, targetId, reason);
        
        // Then
        assertNotNull(auditLog);
        assertEquals(id, auditLog.getId());
        assertEquals(operatorId, auditLog.getOperatorId());
        assertEquals(action, auditLog.getAction());
        assertEquals(targetType, auditLog.getTargetType());
        assertEquals(targetId, auditLog.getTargetId());
        assertEquals(reason, auditLog.getReason());
        assertNotNull(auditLog.getCreatedAt());
    }
    
    @Test
    @DisplayName("创建审计日志 - 无原因")
    void create_shouldCreateAuditLog_whenReasonIsNull() {
        // Given
        Long id = 1L;
        Long operatorId = 789L;
        AuditAction action = AuditAction.ENABLE_USER;
        String targetType = "user";
        Long targetId = 123L;
        
        // When
        AuditLog auditLog = AuditLog.create(id, operatorId, action, targetType, targetId, null);
        
        // Then
        assertNotNull(auditLog);
        assertNull(auditLog.getReason());
    }
    
    @Test
    @DisplayName("创建审计日志 - ID为空时抛出异常")
    void create_shouldThrowException_whenIdIsNull() {
        // Given
        Long operatorId = 789L;
        AuditAction action = AuditAction.DISABLE_USER;
        String targetType = "user";
        Long targetId = 123L;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            AuditLog.create(null, operatorId, action, targetType, targetId, null)
        );
    }
    
    @Test
    @DisplayName("创建审计日志 - 操作者ID为空时抛出异常")
    void create_shouldThrowException_whenOperatorIdIsEmpty() {
        // Given
        Long id = 1L;
        AuditAction action = AuditAction.DISABLE_USER;
        String targetType = "user";
        Long targetId = 123L;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            AuditLog.create(id, null, action, targetType, targetId, null)
        );
    }
    
    @Test
    @DisplayName("创建审计日志 - 操作类型为空时抛出异常")
    void create_shouldThrowException_whenActionIsNull() {
        // Given
        Long id = 1L;
        Long operatorId = 789L;
        String targetType = "user";
        Long targetId = 123L;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            AuditLog.create(id, operatorId, null, targetType, targetId, null)
        );
    }
    
    @Test
    @DisplayName("创建审计日志 - 目标类型为空时抛出异常")
    void create_shouldThrowException_whenTargetTypeIsEmpty() {
        // Given
        Long id = 1L;
        Long operatorId = 789L;
        AuditAction action = AuditAction.DISABLE_USER;
        Long targetId = 123L;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            AuditLog.create(id, operatorId, action, "", targetId, null)
        );
    }
    
    @Test
    @DisplayName("创建审计日志 - 目标ID为空时抛出异常")
    void create_shouldThrowException_whenTargetIdIsEmpty() {
        // Given
        Long id = 1L;
        Long operatorId = 789L;
        AuditAction action = AuditAction.DISABLE_USER;
        String targetType = "user";
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            AuditLog.create(id, operatorId, action, targetType, null, null)
        );
    }
    
    @Test
    @DisplayName("从持久化数据恢复审计日志")
    void reconstitute_shouldRestoreAuditLog() {
        // Given
        Long id = 1L;
        Long operatorId = 789L;
        AuditAction action = AuditAction.DELETE_POST;
        String targetType = "post";
        Long targetId = 100L;
        String reason = "违规内容";
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
        
        // When
        AuditLog auditLog = AuditLog.reconstitute(id, operatorId, action, targetType, targetId, reason, createdAt);
        
        // Then
        assertNotNull(auditLog);
        assertEquals(id, auditLog.getId());
        assertEquals(operatorId, auditLog.getOperatorId());
        assertEquals(action, auditLog.getAction());
        assertEquals(targetType, auditLog.getTargetType());
        assertEquals(targetId, auditLog.getTargetId());
        assertEquals(reason, auditLog.getReason());
        assertEquals(createdAt, auditLog.getCreatedAt());
    }
    
    @Test
    @DisplayName("验证所有审计操作类型")
    void auditAction_shouldHaveAllExpectedValues() {
        // Then
        assertEquals(8, AuditAction.values().length);
        assertNotNull(AuditAction.DISABLE_USER);
        assertNotNull(AuditAction.ENABLE_USER);
        assertNotNull(AuditAction.DELETE_POST);
        assertNotNull(AuditAction.DELETE_COMMENT);
        assertNotNull(AuditAction.HANDLE_REPORT);
        assertNotNull(AuditAction.ASSIGN_ROLE);
        assertNotNull(AuditAction.REVOKE_ROLE);
        assertNotNull(AuditAction.WARN_USER);
    }
}
