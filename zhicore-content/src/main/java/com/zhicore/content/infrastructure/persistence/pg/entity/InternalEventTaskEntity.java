package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 内容服务内部事件任务实体。
 */
@Data
@TableName("domain_event_task")
public class InternalEventTaskEntity {

    public static final int PRIORITY_HIGH = 0;
    public static final int PRIORITY_NORMAL = 100;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String eventType;

    private Long aggregateId;

    private Long aggregateVersion;

    private Integer schemaVersion;

    private String payload;

    /**
     * 数值越小优先级越高。
     */
    private Integer priority;

    private Instant occurredAt;

    private Instant nextAttemptAt;

    private Instant claimedAt;

    private String claimedBy;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant dispatchedAt;

    private Integer retryCount;

    private String lastError;

    private InternalEventTaskStatus status;

    public enum InternalEventTaskStatus {
        PENDING,
        PROCESSING,
        FAILED,
        SUCCEEDED,
        @Deprecated
        DISPATCHED,
        DEAD
    }
}
