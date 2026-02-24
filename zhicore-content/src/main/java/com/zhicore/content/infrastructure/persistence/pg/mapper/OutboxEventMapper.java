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
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'DISPATCHED' AND dispatched_at >= #{since}")
    long countDispatchedSince(@Param("since") Instant since);
    
    /**
     * 统计指定时间后失败的消息数量
     * 
     * 用于监控失败率
     * 
     * @param since 起始时间
     * @return 失败的消息数量
     */
    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'FAILED' AND updated_at >= #{since}")
    long countFailedSince(@Param("since") Instant since);
}
