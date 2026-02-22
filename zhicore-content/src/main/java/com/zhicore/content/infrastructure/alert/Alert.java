package com.zhicore.content.infrastructure.alert;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警信息
 */
@Data
@Builder
public class Alert {
    
    /**
     * 告警ID
     */
    private String id;
    
    /**
     * 告警类型
     */
    private AlertType type;
    
    /**
     * 告警级别
     */
    private AlertLevel level;
    
    /**
     * 告警标题
     */
    private String title;
    
    /**
     * 告警消息
     */
    private String message;
    
    /**
     * 告警详情
     */
    private String details;
    
    /**
     * 告警时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 是否已发送
     */
    private boolean sent;
    
    /**
     * 相关资源ID（如文章ID、用户ID等）
     */
    private String resourceId;
}
