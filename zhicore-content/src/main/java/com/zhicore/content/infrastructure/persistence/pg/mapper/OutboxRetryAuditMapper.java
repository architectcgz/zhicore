package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxRetryAuditEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * Outbox 手动重试审计 Mapper（R14）
 */
@Mapper
public interface OutboxRetryAuditMapper extends BaseMapper<OutboxRetryAuditEntity> {

    /**
     * 统计指定时间窗口内某事件的重试次数（用于 10 分钟限频）
     */
    @Select("""
            SELECT COUNT(*)
            FROM outbox_retry_audit
            WHERE event_id = #{eventId}
              AND retried_at >= #{since}
            """)
    long countRecentRetries(@Param("eventId") String eventId, @Param("since") Instant since);
}

