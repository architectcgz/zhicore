package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.ConsumedEventEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 已消费事件 Mapper
 * 
 * 提供事件消费记录的数据库操作
 */
@Mapper
public interface ConsumedEventMapper extends BaseMapper<ConsumedEventEntity> {
    /**
     * 删除指定时间之前的消费记录
     * 
     * 用于定期清理过期记录，避免表无限增长
     * 
     * @param before 清理此时间之前的记录
     * @return 删除的记录数量
     */
    @Delete("DELETE FROM consumed_events WHERE consumed_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
