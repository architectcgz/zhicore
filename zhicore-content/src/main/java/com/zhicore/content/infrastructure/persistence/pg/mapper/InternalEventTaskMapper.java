package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * 内容服务内部事件任务 Mapper。
 */
@Mapper
public interface InternalEventTaskMapper extends BaseMapper<InternalEventTaskEntity> {

    @Select("SELECT COUNT(*) FROM domain_event_task WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT MIN(created_at) FROM domain_event_task WHERE status = 'PENDING'")
    Instant findOldestPendingCreatedAt();

    @Select("""
            SELECT COUNT(*)
            FROM domain_event_task
            WHERE status IN ('SUCCEEDED', 'DISPATCHED')
              AND dispatched_at >= #{since}
            """)
    long countSucceededSince(@Param("since") Instant since);

    @Select("""
            SELECT COUNT(*)
            FROM domain_event_task
            WHERE status = 'FAILED'
              AND (retry_count IS NULL OR retry_count < #{maxRetry})
              AND updated_at >= #{since}
            """)
    long countFailedSince(@Param("since") Instant since, @Param("maxRetry") int maxRetry);

    @Select("""
            SELECT COUNT(*)
            FROM domain_event_task
            WHERE (
                    status = 'DEAD'
                 OR (status = 'FAILED' AND retry_count >= #{maxRetry})
                  )
              AND updated_at >= #{since}
            """)
    long countDeadSince(@Param("since") Instant since, @Param("maxRetry") int maxRetry);

    /**
     * 只允许 claim 每个 aggregate 的“最早未完成事件”，避免同一聚合并发乱序。
     */
    @Select("""
            WITH head_per_aggregate AS (
                SELECT DISTINCT ON (COALESCE(aggregate_id, -id))
                    id
                FROM domain_event_task
                WHERE status NOT IN ('SUCCEEDED', 'DISPATCHED')
                ORDER BY COALESCE(aggregate_id, -id),
                         aggregate_version ASC NULLS FIRST,
                         created_at ASC,
                         id ASC
            ),
            claimed AS (
                SELECT task.id
                FROM domain_event_task task
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
                ORDER BY task.priority ASC, task.created_at ASC
                LIMIT #{limit}
                FOR UPDATE OF task SKIP LOCKED
            )
            UPDATE domain_event_task target
            SET status = 'PROCESSING',
                claimed_at = #{now},
                claimed_by = #{claimedBy},
                updated_at = #{now}
            FROM claimed
            WHERE target.id = claimed.id
            RETURNING target.*
            """)
    List<InternalEventTaskEntity> claimDispatchable(@Param("now") Instant now,
                                                    @Param("reclaimBefore") Instant reclaimBefore,
                                                    @Param("claimedBy") String claimedBy,
                                                    @Param("limit") int limit);
}
