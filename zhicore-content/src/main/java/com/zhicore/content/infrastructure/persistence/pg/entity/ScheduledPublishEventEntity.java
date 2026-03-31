package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 定时发布事件实体（R1）
 *
 * 用于对“定时发布”主链路进行可观测、可重试、可扫描兜底的状态管理。
 *
 * 注意：
 * - 时间比较必须以数据库时间为准（SELECT CURRENT_TIMESTAMP），避免应用节点时钟漂移。
 * - next_attempt_at 统一表达“下次可被补偿/重试处理的时间”。
 * - claimed_at / claimed_by 用于 claim 生命周期与超时回收。
 */
@Data
@TableName("scheduled_publish_event")
public class ScheduledPublishEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 定时发布任务ID（稳定标识）
     */
    private String eventId;

    /**
     * 最近一次投递到 MQ 的触发事件ID（会随每次重发变化）
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String triggerEventId;

    private Long postId;

    private OffsetDateTime scheduledAt;

    private OffsetDateTime nextAttemptAt;

    private ScheduledPublishStatus status;

    private Integer rescheduleRetryCount;

    private Integer publishRetryCount;

    /**
     * 当前 claim 时间
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private OffsetDateTime claimedAt;

    /**
     * 当前 claim worker
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String claimedBy;

    /**
     * 最近一次错误信息
     *
     * 说明：成功收敛时需要清空错误信息（更新为 NULL），同样需要允许 NULL 值更新。
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String lastError;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public enum ScheduledPublishStatus {
        /**
         * 待处理/待补偿
         */
        PENDING,
        /**
         * 已被某个 worker/消费者 claim
         */
        PROCESSING,
        /**
         * 可重试失败
         */
        FAILED,
        /**
         * 已成功收敛（包括取消定时后的终态）
         */
        SUCCEEDED,
        /**
         * 需要人工介入的终态失败
         */
        DEAD
    }
}
