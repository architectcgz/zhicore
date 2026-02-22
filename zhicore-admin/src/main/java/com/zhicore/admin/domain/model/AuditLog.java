package com.zhicore.admin.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 * 记录管理员的所有操作行为
 */
@Getter
public class AuditLog {
    
    /**
     * 审计日志ID
     */
    private Long id;
    
    /**
     * 操作者ID（管理员ID）
     */
    private Long operatorId;
    
    /**
     * 操作类型
     */
    private AuditAction action;
    
    /**
     * 目标类型（user/post/comment/report）
     */
    private String targetType;
    
    /**
     * 目标ID
     */
    private Long targetId;
    
    /**
     * 操作原因/备注
     */
    private String reason;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 私有构造函数
     */
    private AuditLog() {
    }
    
    /**
     * 创建审计日志
     *
     * @param id         日志ID
     * @param operatorId 操作者ID
     * @param action     操作类型
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param reason     操作原因
     * @return 审计日志实例
     */
    public static AuditLog create(Long id, Long operatorId, AuditAction action,
                                  String targetType, Long targetId, String reason) {
        Assert.notNull(id, "审计日志ID不能为空");
        Assert.isTrue(id > 0, "审计日志ID必须为正数");
        Assert.notNull(operatorId, "操作者ID不能为空");
        Assert.isTrue(operatorId > 0, "操作者ID必须为正数");
        Assert.notNull(action, "操作类型不能为空");
        Assert.hasText(targetType, "目标类型不能为空");
        Assert.notNull(targetId, "目标ID不能为空");
        Assert.isTrue(targetId > 0, "目标ID必须为正数");
        
        AuditLog log = new AuditLog();
        log.id = id;
        log.operatorId = operatorId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.reason = reason;
        log.createdAt = LocalDateTime.now();
        return log;
    }
    
    /**
     * 从持久化数据恢复
     */
    public static AuditLog reconstitute(Long id, Long operatorId, AuditAction action,
                                        String targetType, Long targetId, String reason,
                                        LocalDateTime createdAt) {
        AuditLog log = new AuditLog();
        log.id = id;
        log.operatorId = operatorId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.reason = reason;
        log.createdAt = createdAt;
        return log;
    }
}
