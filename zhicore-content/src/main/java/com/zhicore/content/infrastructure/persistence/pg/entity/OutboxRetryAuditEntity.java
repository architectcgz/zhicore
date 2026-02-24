package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Outbox 手动重试审计记录（R14）
 *
 * 用于记录管理端人工重试的操作者与原因，并支撑“同事件 10 分钟限频”的判定。
 */
@Data
@TableName("outbox_retry_audit")
public class OutboxRetryAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private Long operatorId;

    private String reason;

    private Instant retriedAt;

    /**
     * 重试结果（例如 ACCEPTED/REJECTED/FAILED）
     */
    private String result;

    private Instant createdAt;
}

