package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 定时发布事件 Mapper（R1）
 */
@Mapper
public interface ScheduledPublishEventMapper extends BaseMapper<ScheduledPublishEventEntity> {

    @Select("SELECT COUNT(*) FROM scheduled_publish_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT MIN(created_at) FROM scheduled_publish_event WHERE status = 'PENDING'")
    OffsetDateTime findOldestPendingCreatedAt();

    @Select("""
            SELECT COUNT(*)
            FROM scheduled_publish_event
            WHERE status = 'SUCCEEDED'
              AND updated_at >= #{since}
            """)
    long countSucceededSince(@Param("since") OffsetDateTime since);

    @Select("""
            SELECT COUNT(*)
            FROM scheduled_publish_event
            WHERE status = 'FAILED'
              AND updated_at >= #{since}
            """)
    long countFailedSince(@Param("since") OffsetDateTime since);

    @Select("""
            SELECT COUNT(*)
            FROM scheduled_publish_event
            WHERE status = 'DEAD'
              AND updated_at >= #{since}
            """)
    long countDeadSince(@Param("since") OffsetDateTime since);

    /**
     * 获取数据库当前时间（用于统一时间基准）
     */
    // PostgreSQL 的 CURRENT_TIMESTAMP 返回 TIMESTAMPTZ（带时区），与 OffsetDateTime 不匹配；
    // 这里使用 LOCALTIMESTAMP 返回 timestamp（无时区），避免驱动类型转换异常。
    @Select("SELECT LOCALTIMESTAMP")
    OffsetDateTime selectDbNow();

    /**
     * claim 一批需要补偿的定时发布任务。
     */
    @Select("""
            WITH claimable AS (
                SELECT task.id
                FROM scheduled_publish_event task
                WHERE (
                        task.status IN ('PENDING', 'FAILED')
                        AND task.next_attempt_at <= #{now}
                    )
                   OR (
                        task.status = 'PROCESSING'
                        AND task.claimed_at IS NOT NULL
                        AND task.claimed_at <= #{reclaimBefore}
                    )
                ORDER BY task.next_attempt_at ASC, task.scheduled_at ASC, task.id ASC
                LIMIT #{limit}
                FOR UPDATE OF task SKIP LOCKED
            )
            UPDATE scheduled_publish_event target
            SET status = 'PROCESSING',
                claimed_at = #{now},
                claimed_by = #{claimedBy},
                updated_at = #{now}
            FROM claimable
            WHERE target.id = claimable.id
            RETURNING target.*
            """)
    List<ScheduledPublishEventEntity> claimCompensationBatch(
            @Param("now") OffsetDateTime now,
            @Param("reclaimBefore") OffsetDateTime reclaimBefore,
            @Param("claimedBy") String claimedBy,
            @Param("limit") int limit
    );

    /**
     * claim 单条被消息触发的定时发布任务。
     */
    @Select("""
            WITH claimable AS (
                SELECT task.id
                FROM scheduled_publish_event task
                WHERE task.event_id = #{eventId}
                  AND (
                        task.status IN ('PENDING', 'FAILED')
                     OR (
                            task.status = 'PROCESSING'
                        AND task.claimed_at IS NOT NULL
                        AND task.claimed_at <= #{reclaimBefore}
                     )
                  )
                LIMIT 1
                FOR UPDATE OF task SKIP LOCKED
            )
            UPDATE scheduled_publish_event target
            SET status = 'PROCESSING',
                claimed_at = #{now},
                claimed_by = #{claimedBy},
                updated_at = #{now}
            FROM claimable
            WHERE target.id = claimable.id
            RETURNING target.*
            """)
    List<ScheduledPublishEventEntity> claimForConsumption(
            @Param("eventId") String eventId,
            @Param("now") OffsetDateTime now,
            @Param("reclaimBefore") OffsetDateTime reclaimBefore,
            @Param("claimedBy") String claimedBy
    );

    /**
     * 将指定 post 的所有活动任务收敛为终态。
     */
    @Update("""
            UPDATE scheduled_publish_event
            SET status = #{status},
                next_attempt_at = #{dbNow},
                claimed_at = NULL,
                claimed_by = NULL,
                last_error = #{lastError},
                updated_at = #{dbNow}
            WHERE post_id = #{postId}
              AND status IN ('PENDING', 'PROCESSING', 'FAILED')
            """)
    int markTerminalByPostId(
            @Param("postId") Long postId,
            @Param("status") String status,
            @Param("dbNow") OffsetDateTime dbNow,
            @Param("lastError") String lastError
    );
}
