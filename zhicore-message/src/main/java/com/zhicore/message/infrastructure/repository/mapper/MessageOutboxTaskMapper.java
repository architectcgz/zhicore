package com.zhicore.message.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.message.infrastructure.repository.po.MessageOutboxTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 消息 Outbox 任务 Mapper。
 */
@Mapper
public interface MessageOutboxTaskMapper extends BaseMapper<MessageOutboxTaskPO> {

    @Select("SELECT COUNT(*) FROM message_outbox_task WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT MIN(created_at) FROM message_outbox_task WHERE status = 'PENDING'")
    OffsetDateTime findOldestPendingCreatedAt();

    @Select("""
            SELECT COUNT(*)
            FROM message_outbox_task
            WHERE status IN ('SUCCEEDED', 'DISPATCHED')
              AND dispatched_at >= #{since}
            """)
    long countSucceededSince(@Param("since") OffsetDateTime since);

    @Select("""
            SELECT COUNT(*)
            FROM message_outbox_task
            WHERE status = 'FAILED'
              AND retry_count < #{maxRetry}
              AND updated_at >= #{since}
            """)
    long countFailedSince(@Param("since") OffsetDateTime since, @Param("maxRetry") int maxRetry);

    @Select("""
            SELECT COUNT(*)
            FROM message_outbox_task
            WHERE (
                    status = 'DEAD'
                 OR (status = 'FAILED' AND retry_count >= #{maxRetry})
                  )
              AND updated_at >= #{since}
            """)
    long countDeadSince(@Param("since") OffsetDateTime since, @Param("maxRetry") int maxRetry);

    /**
     * claim 一批当前可执行的任务。
     *
     * <p>约束：
     * - 同一 conversation 只允许最早未完成任务进入处理，避免副作用乱序
     * - PROCESSING 超时任务允许被重新 claim
     */
    @Select("""
            WITH head_per_aggregate AS (
                SELECT DISTINCT ON (aggregate_id)
                    id
                FROM message_outbox_task
                WHERE status NOT IN ('SUCCEEDED', 'DISPATCHED')
                ORDER BY aggregate_id, created_at ASC, id ASC
            ),
            claimed AS (
                SELECT task.id
                FROM message_outbox_task task
                JOIN head_per_aggregate head ON head.id = task.id
                WHERE (
                        task.status IN ('PENDING', 'FAILED')
                        AND task.next_attempt_at <= #{now}
                    )
                   OR (
                        task.status = 'PROCESSING'
                        AND task.claimed_at IS NOT NULL
                        AND task.claimed_at <= #{reclaimBefore}
                    )
                ORDER BY task.created_at ASC, task.id ASC
                LIMIT #{limit}
                FOR UPDATE OF task SKIP LOCKED
            )
            UPDATE message_outbox_task target
            SET status = 'PROCESSING',
                claimed_at = #{now},
                claimed_by = #{claimedBy},
                updated_at = #{now}
            FROM claimed
            WHERE target.id = claimed.id
            RETURNING target.*
            """)
    List<MessageOutboxTaskPO> claimDispatchable(@Param("now") OffsetDateTime now,
                                                @Param("reclaimBefore") OffsetDateTime reclaimBefore,
                                                @Param("claimedBy") String claimedBy,
                                                @Param("limit") int limit);
}
