package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件 Mapper
 * 
 * 提供 Outbox 事件的数据库操作
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventEntity> {
    
    /**
     * 按状态查询事件，按创建时间升序排列
     * 
     * 用于投递器扫描待投递的事件
     * 
     * @param status 事件状态
     * @param limit 查询数量限制
     * @return 事件列表
     */
    @Select("SELECT * FROM outbox_event WHERE status = #{status} ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(
        @Param("status") String status, 
        @Param("limit") int limit
    );

    /**
     * claim 一批可投递 outbox 事件。
     *
     * <p>说明：
     * - PENDING + next_attempt_at 到期的任务可以直接 claim
     * - PROCESSING 但 claim 超时的任务允许被回收
     * - 同一 aggregate 只会 claim 最早未完成的那条，保证投递顺序
     */
    @Select("""
            WITH head_per_aggregate AS (
                SELECT DISTINCT ON (COALESCE(aggregate_id, -id))
                    id
                FROM outbox_event
                WHERE status NOT IN ('SUCCEEDED', 'DISPATCHED')
                ORDER BY COALESCE(aggregate_id, -id),
                         aggregate_version ASC NULLS FIRST,
                         created_at ASC,
                         id ASC
            ),
            claimed AS (
                SELECT task.id
                FROM outbox_event task
                JOIN head_per_aggregate head ON head.id = task.id
                WHERE (
                        task.status = 'PENDING'
                        AND task.next_attempt_at <= #{now}
                    )
                   OR (
                        task.status = 'FAILED'
                        AND (task.retry_count IS NULL OR task.retry_count < #{maxRetry})
                        AND task.next_attempt_at <= #{now}
                    )
                   OR (
                        task.status = 'PROCESSING'
                        AND task.claimed_at IS NOT NULL
                        AND task.claimed_at <= #{reclaimBefore}
                    )
                ORDER BY task.created_at ASC
                LIMIT #{limit}
                FOR UPDATE OF task SKIP LOCKED
            )
            UPDATE outbox_event target
            SET status = 'PROCESSING',
                claimed_at = #{now},
                claimed_by = #{claimedBy},
                updated_at = #{now}
            FROM claimed
            WHERE target.id = claimed.id
            RETURNING target.*
            """)
    List<OutboxEventEntity> claimDispatchable(@Param("now") Instant now,
                                              @Param("reclaimBefore") Instant reclaimBefore,
                                              @Param("claimedBy") String claimedBy,
                                              @Param("limit") int limit,
                                              @Param("maxRetry") int maxRetry);
    
    /**
     * 按聚合根ID查询事件
     * 
     * 用于查询特定聚合根的所有事件
     * 
     * @param aggregateId 聚合根ID
     * @return 事件列表
     */
    @Select("SELECT * FROM outbox_event WHERE aggregate_id = #{aggregateId} ORDER BY aggregate_version ASC")
    List<OutboxEventEntity> findByAggregateId(@Param("aggregateId") Long aggregateId);
    
    // ==================== Outbox 堆积监控查询方法 ====================
    
    /**
     * 统计指定状态的事件数量
     * 
     * 用于监控 PENDING 消息堆积情况
     * 
     * @param status 事件状态
     * @return 事件数量
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
    
    /**
     * 查找最老的 PENDING 消息创建时间
     * 
     * 用于监控消息堆积年龄
     * 
     * @return 最老消息的创建时间，如果没有 PENDING 消息则返回 null
     */
    @Select("SELECT MIN(created_at) FROM outbox_event WHERE status = 'PENDING'")
    Instant findOldestPendingCreatedAt();
    
    /**
     * 统计指定时间后投递成功的消息数量
     * 
     * 用于监控投递速率
     * 
     * @param since 起始时间
     * @return 投递成功的消息数量
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status IN ('SUCCEEDED', 'DISPATCHED') AND dispatched_at >= #{since}")
    long countSucceededSince(@Param("since") Instant since);
    
    /**
     * 统计指定时间后失败的消息数量
     * 
     * 用于监控失败率
     * 
     * @param since 起始时间
     * @return 失败的消息数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM outbox_event
            WHERE status = 'FAILED'
              AND (retry_count IS NULL OR retry_count < #{maxRetry})
              AND updated_at >= #{since}
            """)
    long countFailedSince(@Param("since") Instant since, @Param("maxRetry") int maxRetry);

    @Select("""
            SELECT COUNT(*)
            FROM outbox_event
            WHERE (
                    status = 'DEAD'
                 OR (status = 'FAILED' AND retry_count >= #{maxRetry})
                  )
              AND updated_at >= #{since}
            """)
    long countDeadSince(@Param("since") Instant since, @Param("maxRetry") int maxRetry);
}
