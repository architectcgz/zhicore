package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时发布事件 Mapper（R1）
 */
@Mapper
public interface ScheduledPublishEventMapper extends BaseMapper<ScheduledPublishEventEntity> {

    /**
     * 获取数据库当前时间（用于统一时间基准）
     */
    // PostgreSQL 的 CURRENT_TIMESTAMP 返回 TIMESTAMPTZ（带时区），与 LocalDateTime 不匹配；
    // 这里使用 LOCALTIMESTAMP 返回 timestamp（无时区），避免驱动类型转换异常。
    @Select("SELECT LOCALTIMESTAMP")
    LocalDateTime selectDbNow();

    /**
     * 扫描到点且满足冷却期的 SCHEDULED_PENDING 事件
     */
    @Select("""
            SELECT *
            FROM scheduled_publish_event
            WHERE status = 'SCHEDULED_PENDING'
              AND scheduled_at <= #{dbNow}
              AND (last_enqueue_at IS NULL OR last_enqueue_at < #{cooldownBefore})
            ORDER BY scheduled_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ScheduledPublishEventEntity> findDueScheduledPending(
            @Param("dbNow") LocalDateTime dbNow,
            @Param("cooldownBefore") LocalDateTime cooldownBefore,
            @Param("limit") int limit
    );

    /**
     * 补扫：扫描 last_enqueue_at 超时（>10分钟）且到点的事件
     */
    @Select("""
            SELECT *
            FROM scheduled_publish_event
            WHERE status = 'SCHEDULED_PENDING'
              AND scheduled_at <= #{dbNow}
              AND last_enqueue_at IS NOT NULL
              AND last_enqueue_at < #{staleBefore}
            ORDER BY scheduled_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ScheduledPublishEventEntity> findStaleScheduledPending(
            @Param("dbNow") LocalDateTime dbNow,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("limit") int limit
    );

    /**
     * 扫描入队 CAS 闸门：last_enqueue_at 为 NULL 的场景
     *
     * 注意：WHERE 条件必须包含 status + scheduled_at + last_enqueue_at，且以 affected_rows==1 作为唯一入队凭证。
     */
    @Update("""
            UPDATE scheduled_publish_event
            SET last_enqueue_at = #{dbNow},
                event_id = #{newEventId},
                updated_at = #{dbNow}
            WHERE id = #{id}
              AND status = 'SCHEDULED_PENDING'
              AND scheduled_at = #{scheduledAt}
              AND last_enqueue_at IS NULL
            """)
    int casUpdateLastEnqueueAtWhenNull(
            @Param("id") Long id,
            @Param("scheduledAt") LocalDateTime scheduledAt,
            @Param("dbNow") LocalDateTime dbNow,
            @Param("newEventId") String newEventId
    );

    /**
     * 扫描入队 CAS 闸门：last_enqueue_at 非 NULL 的场景
     */
    @Update("""
            UPDATE scheduled_publish_event
            SET last_enqueue_at = #{dbNow},
                event_id = #{newEventId},
                updated_at = #{dbNow}
            WHERE id = #{id}
              AND status = 'SCHEDULED_PENDING'
              AND scheduled_at = #{scheduledAt}
              AND last_enqueue_at = #{expectedLastEnqueueAt}
            """)
    int casUpdateLastEnqueueAt(
            @Param("id") Long id,
            @Param("scheduledAt") LocalDateTime scheduledAt,
            @Param("expectedLastEnqueueAt") LocalDateTime expectedLastEnqueueAt,
            @Param("dbNow") LocalDateTime dbNow,
            @Param("newEventId") String newEventId
    );
}
