package com.zhicore.admin.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报视图对象
 */
@Data
@Builder
public class ReportVO {
    
    /**
     * 举报ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    /**
     * 举报人ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reporterId;
    
    /**
     * 被举报用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportedUserId;
    
    /**
     * 目标类型
     */
    private String targetType;
    
    /**
     * 目标ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    
    /**
     * 举报原因
     */
    private String reason;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 处理人ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handlerId;
    
    /**
     * 处理动作
     */
    private String handleAction;
    
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
}
