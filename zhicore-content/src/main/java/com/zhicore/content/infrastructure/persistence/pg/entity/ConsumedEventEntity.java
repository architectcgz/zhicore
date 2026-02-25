package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已消费事件实体
 * 
 * 用于记录已消费的事件，实现幂等性
 * 通过 eventId 主键约束防止重复消费
 */
@Data
@TableName("consumed_events")
public class ConsumedEventEntity {
    /**
     * 事件 ID（主键）
     * 
     * 使用事件的唯一标识作为主键，保证幂等性
     */
    @TableId(type = IdType.INPUT)
    private String eventId;
    
    /**
     * 事件类型
     * 
     * 例如：UserProfileUpdated, StatsUpdated
     */
    private String eventType;
    
    /**
     * 消费者名称
     * 
     * 例如：zhicore-content-profile-consumer
     */
    private String consumerName;
    
    /**
     * 消费时间
     * 
     * 记录事件被消费的时间，用于定期清理
     */
    private LocalDateTime consumedAt;
}
