package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Outbox 事件实体
 * 
 * 用于实现 Outbox 模式，保证集成事件的可靠投递
 * 事务内写入 Outbox 表，事务后由后台投递器异步发送到 RocketMQ
 */
@Data
@TableName("outbox_event")
public class OutboxEventEntity {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 事件唯一标识（与领域事件保持一致）
     */
    private String eventId;
    
    /**
     * 事件类型（用于反序列化）
     * 
     * 例如：com.zhicore.integration.messaging.post.PostCreatedIntegrationEvent
     */
    private String eventType;
    
    /**
     * 聚合根ID
     */
    private Long aggregateId;
    
    /**
     * 聚合根版本号（用于并发控制和事件顺序）
     */
    private Long aggregateVersion;
    
    /**
     * 事件Schema版本（用于消息演进和向后兼容）
     */
    private Integer schemaVersion;
    
    /**
     * JSON格式的事件载荷
     */
    private String payload;
    
    /**
     * 事件发生时间（UTC）
     */
    private Instant occurredAt;
    
    /**
     * Outbox记录创建时间
     */
    private Instant createdAt;

    /**
     * Outbox 记录更新时间（用于运维侧排查与监控统计口径）
     */
    private Instant updatedAt;
    
    /**
     * 投递成功时间
     */
    private Instant dispatchedAt;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 最后一次错误信息
     */
    private String lastError;
    
    /**
     * 投递状态
     */
    private OutboxStatus status;
    
    /**
     * Outbox 投递状态枚举
     */
    public enum OutboxStatus {
        /**
         * 待投递
         */
        PENDING,
        
        /**
         * 已投递
         */
        DISPATCHED,
        
        /**
         * 失败（超过最大重试次数）
         */
        FAILED
    }
}
