package com.zhicore.content.application.port.repo;

import java.time.LocalDateTime;

/**
 * 已消费事件仓储
 * 
 * 用于实现事件消费的幂等性，通过数据库主键约束防止重复消费
 */
public interface ConsumedEventRepository {
    /**
     * 尝试插入消费记录
     * 
     * 如果 eventId 已存在则返回 false，否则插入并返回 true
     * 使用数据库主键约束实现幂等性检查
     * 
     * @param eventId 事件 ID（唯一标识）
     * @param eventType 事件类型（如 UserProfileUpdated, StatsUpdated）
     * @param consumerName 消费者名称（如 zhicore-content-profile-consumer）
     * @return 是否是新事件（true=首次消费，false=重复消费）
     */
    boolean tryInsert(String eventId, String eventType, String consumerName);
    
    /**
     * 清理过期的消费记录
     * 
     * 定期清理旧记录，避免 consumed_events 表无限增长
     * 
     * @param before 清理此时间之前的记录
     * @return 清理的记录数量
     */
    int cleanupBefore(LocalDateTime before);
}
