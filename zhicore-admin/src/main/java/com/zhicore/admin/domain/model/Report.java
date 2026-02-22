package com.zhicore.admin.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * 举报实体
 */
@Getter
public class Report {
    
    /**
     * 举报ID
     */
    private Long id;
    
    /**
     * 举报人ID
     */
    private Long reporterId;
    
    /**
     * 被举报用户ID
     */
    private Long reportedUserId;
    
    /**
     * 目标类型（post/comment）
     */
    private String targetType;
    
    /**
     * 目标ID
     */
    private Long targetId;
    
    /**
     * 举报原因
     */
    private String reason;
    
    /**
     * 举报状态
     */
    private ReportStatus status;
    
    /**
     * 处理人ID
     */
    private Long handlerId;
    
    /**
     * 处理动作
     */
    private ReportHandleAction handleAction;
    
    /**
     * 处理备注
     */
    private String handleRemark;
    
    /**
     * 处理时间
     */
    private LocalDateTime handledAt;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 私有构造函数
     */
    private Report() {
    }
    
    /**
     * 从持久化数据恢复
     */
    public static Report reconstitute(Long id, Long reporterId, Long reportedUserId,
                                      String targetType, Long targetId, String reason,
                                      ReportStatus status, Long handlerId,
                                      ReportHandleAction handleAction, String handleRemark,
                                      LocalDateTime handledAt, LocalDateTime createdAt) {
        Report report = new Report();
        report.id = id;
        report.reporterId = reporterId;
        report.reportedUserId = reportedUserId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.status = status;
        report.handlerId = handlerId;
        report.handleAction = handleAction;
        report.handleRemark = handleRemark;
        report.handledAt = handledAt;
        report.createdAt = createdAt;
        return report;
    }
    
    /**
     * 处理举报
     *
     * @param handlerId    处理人ID
     * @param action       处理动作
     * @param handleRemark 处理备注
     */
    public void handle(Long handlerId, ReportHandleAction action, String handleRemark) {
        Assert.notNull(handlerId, "处理人ID不能为空");
        Assert.isTrue(handlerId > 0, "处理人ID必须为正数");
        Assert.notNull(action, "处理动作不能为空");
        
        if (this.status != ReportStatus.PENDING) {
            throw new IllegalStateException("举报已处理，不能重复处理");
        }
        
        this.handlerId = handlerId;
        this.handleAction = action;
        this.handleRemark = handleRemark;
        this.handledAt = LocalDateTime.now();
        this.status = action == ReportHandleAction.IGNORE ? ReportStatus.IGNORED : ReportStatus.PROCESSED;
    }
    
    /**
     * 是否待处理
     */
    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }
}
