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

    @Select("""
            SELECT *
            FROM domain_event_task
            WHERE status IN ('PENDING', 'FAILED')
              AND next_attempt_at <= #{now}
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<InternalEventTaskEntity> findDispatchable(@Param("now") Instant now, @Param("limit") int limit);
}
