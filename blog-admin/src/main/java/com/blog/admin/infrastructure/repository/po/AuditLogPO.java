package com.blog.admin.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 审计日志持久化对象
 */
@Data
@TableName("audit_logs")
public class AuditLogPO {
    
    @TableId(type = IdType.INPUT)
    private Long id;
    
    private Long operatorId;
    
    private String action;
    
    private String targetType;
    
    private Long targetId;
    
    private String reason;
    
    private OffsetDateTime createdAt;
}
