package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时发布事件实体（R1）
 *
 * 用于对“定时发布”主链路进行可观测、可重试、可扫描兜底的状态管理。
 *
 * 注意：
 * - 时间比较必须以数据库时间为准（SELECT CURRENT_TIMESTAMP），避免应用节点时钟漂移。
 * - last_enqueue_at 用作扫描入队的 CAS 闸门，防止多实例/多线程重复入队风暴。
 */
@Data
@TableName("scheduled_publish_event")
public class ScheduledPublishEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件ID（建议与 MQ/Outbox 里的 event_id 保持一致，便于串联排查）
     */
    private String eventId;

    private Long postId;

    private LocalDateTime scheduledAt;

    private ScheduledPublishStatus status;

    private Integer rescheduleRetryCount;

    private Integer publishRetryCount;

    private LocalDateTime lastEnqueueAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum ScheduledPublishStatus {
        /**
         * 已入队（首次）但尚未最终收敛的状态
         */
        PENDING,
        /**
         * 因未到点/重试上限等原因转入扫描兜底队列
         */
        SCHEDULED_PENDING,
        /**
         * 已发布并幂等收敛
         */
        PUBLISHED,
        /**
         * 已失败（达到发布重试上限或状态非法/文章不存在）
         */
        FAILED
    }
}

