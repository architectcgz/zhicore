package com.blog.admin.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 举报领域模型测试
 */
@DisplayName("Report 领域模型测试")
class ReportTest {
    
    @Test
    @DisplayName("从持久化数据恢复举报 - 待处理状态")
    void reconstitute_shouldRestorePendingReport() {
        // Given
        Long id = 1L;
        Long reporterId = 123L;
        Long reportedUserId = 456L;
        String targetType = "post";
        Long targetId = 100L;
        String reason = "违规内容";
        ReportStatus status = ReportStatus.PENDING;
        LocalDateTime createdAt = LocalDateTime.now();
        
        // When
        Report report = Report.reconstitute(
                id, reporterId, reportedUserId, targetType, targetId, reason,
                status, null, null, null, null, createdAt
        );
        
        // Then
        assertNotNull(report);
        assertEquals(id, report.getId());
        assertEquals(reporterId, report.getReporterId());
        assertEquals(reportedUserId, report.getReportedUserId());
        assertEquals(targetType, report.getTargetType());
        assertEquals(targetId, report.getTargetId());
        assertEquals(reason, report.getReason());
        assertEquals(status, report.getStatus());
        assertTrue(report.isPending());
        assertNull(report.getHandlerId());
        assertNull(report.getHandleAction());
    }
    
    @Test
    @DisplayName("从持久化数据恢复举报 - 已处理状态")
    void reconstitute_shouldRestoreProcessedReport() {
        // Given
        Long id = 1L;
        Long reporterId = 123L;
        Long reportedUserId = 456L;
        String targetType = "comment";
        Long targetId = 200L;
        String reason = "恶意评论";
        ReportStatus status = ReportStatus.PROCESSED;
        Long handlerId = 789L;
        ReportHandleAction handleAction = ReportHandleAction.DELETE_CONTENT;
        String handleRemark = "已删除违规评论";
        LocalDateTime handledAt = LocalDateTime.now();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        
        // When
        Report report = Report.reconstitute(
                id, reporterId, reportedUserId, targetType, targetId, reason,
                status, handlerId, handleAction, handleRemark, handledAt, createdAt
        );
        
        // Then
        assertNotNull(report);
        assertEquals(status, report.getStatus());
        assertFalse(report.isPending());
        assertEquals(handlerId, report.getHandlerId());
        assertEquals(handleAction, report.getHandleAction());
        assertEquals(handleRemark, report.getHandleRemark());
        assertEquals(handledAt, report.getHandledAt());
    }
    
    @Test
    @DisplayName("处理举报 - 删除内容")
    void handle_shouldProcessReport_whenDeleteContent() {
        // Given
        Report report = createPendingReport();
        Long handlerId = 789L;
        ReportHandleAction action = ReportHandleAction.DELETE_CONTENT;
        String remark = "内容违规，已删除";
        
        // When
        report.handle(handlerId, action, remark);
        
        // Then
        assertEquals(ReportStatus.PROCESSED, report.getStatus());
        assertEquals(handlerId, report.getHandlerId());
        assertEquals(action, report.getHandleAction());
        assertEquals(remark, report.getHandleRemark());
        assertNotNull(report.getHandledAt());
        assertFalse(report.isPending());
    }
    
    @Test
    @DisplayName("处理举报 - 封禁用户")
    void handle_shouldProcessReport_whenBanUser() {
        // Given
        Report report = createPendingReport();
        Long handlerId = 789L;
        ReportHandleAction action = ReportHandleAction.BAN_USER;
        String remark = "多次违规，封禁账号";
        
        // When
        report.handle(handlerId, action, remark);
        
        // Then
        assertEquals(ReportStatus.PROCESSED, report.getStatus());
        assertEquals(action, report.getHandleAction());
    }
    
    @Test
    @DisplayName("处理举报 - 警告用户")
    void handle_shouldProcessReport_whenWarnUser() {
        // Given
        Report report = createPendingReport();
        Long handlerId = 789L;
        ReportHandleAction action = ReportHandleAction.WARN_USER;
        String remark = "首次违规，发出警告";
        
        // When
        report.handle(handlerId, action, remark);
        
        // Then
        assertEquals(ReportStatus.PROCESSED, report.getStatus());
        assertEquals(action, report.getHandleAction());
    }
    
    @Test
    @DisplayName("处理举报 - 忽略")
    void handle_shouldIgnoreReport_whenIgnoreAction() {
        // Given
        Report report = createPendingReport();
        Long handlerId = 789L;
        ReportHandleAction action = ReportHandleAction.IGNORE;
        String remark = "举报内容不属实";
        
        // When
        report.handle(handlerId, action, remark);
        
        // Then
        assertEquals(ReportStatus.IGNORED, report.getStatus());
        assertEquals(action, report.getHandleAction());
        assertFalse(report.isPending());
    }
    
    @Test
    @DisplayName("处理举报 - 处理人ID为空时抛出异常")
    void handle_shouldThrowException_whenHandlerIdIsEmpty() {
        // Given
        Report report = createPendingReport();
        ReportHandleAction action = ReportHandleAction.DELETE_CONTENT;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            report.handle(null, action, "remark")
        );
    }
    
    @Test
    @DisplayName("处理举报 - 处理动作为空时抛出异常")
    void handle_shouldThrowException_whenActionIsNull() {
        // Given
        Report report = createPendingReport();
        Long handlerId = 789L;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            report.handle(handlerId, null, "remark")
        );
    }
    
    @Test
    @DisplayName("处理举报 - 重复处理时抛出异常")
    void handle_shouldThrowException_whenAlreadyProcessed() {
        // Given
        Report report = createPendingReport();
        report.handle(789L, ReportHandleAction.DELETE_CONTENT, "first handle");
        
        // When & Then
        assertThrows(IllegalStateException.class, () -> 
            report.handle(999L, ReportHandleAction.BAN_USER, "second handle")
        );
    }
    
    @Test
    @DisplayName("验证所有举报状态")
    void reportStatus_shouldHaveAllExpectedValues() {
        // Then
        assertEquals(3, ReportStatus.values().length);
        assertNotNull(ReportStatus.PENDING);
        assertNotNull(ReportStatus.PROCESSED);
        assertNotNull(ReportStatus.IGNORED);
    }
    
    @Test
    @DisplayName("验证所有处理动作")
    void reportHandleAction_shouldHaveAllExpectedValues() {
        // Then
        assertEquals(4, ReportHandleAction.values().length);
        assertNotNull(ReportHandleAction.DELETE_CONTENT);
        assertNotNull(ReportHandleAction.WARN_USER);
        assertNotNull(ReportHandleAction.BAN_USER);
        assertNotNull(ReportHandleAction.IGNORE);
    }
    
    private Report createPendingReport() {
        return Report.reconstitute(
                1L,
                123L,
                456L,
                "post",
                100L,
                "违规内容",
                ReportStatus.PENDING,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
    }
}
