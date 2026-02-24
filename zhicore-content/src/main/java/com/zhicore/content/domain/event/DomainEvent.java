package com.zhicore.content.domain.event;

import java.time.Instant;

/**
 * 领域事件基础接口（泛型版本）
 * 
 * 定义所有领域事件的通用契约。
 * 领域事件用于实现事件驱动架构和最终一致性。
 * 
 * 设计要点：
 * - 使用泛型 ID 类型支持不同的聚合根标识类型
 * - 使用 Instant 替代 LocalDateTime，确保时间为 UTC
 * - 区分 aggregateVersion（并发控制）和 schemaVersion（消息演进）
 * 
 * @param <ID> 聚合根ID类型
 * @author ZhiCore Team
 */
public interface DomainEvent<ID> {
    
    /**
     * 获取事件ID（唯一标识）
     * 
     * @return 事件ID（无连字符的UUID）
     */
    String getEventId();
    
    /**
     * 获取事件发生时间（UTC）
     * 
     * @return 发生时间（Instant，UTC时区）
     */
    Instant getOccurredAt();
    
    /**
     * 获取聚合根ID
     * 
     * @return 聚合根ID
     */
    ID getAggregateId();
    
    /**
     * 获取聚合根版本号（用于并发控制）
     * 
     * 该版本号用于乐观锁控制，确保事件按正确顺序处理
     * 
     * @return 聚合根版本号
     */
    Long getAggregateVersion();
    
    /**
     * 获取事件模式版本号（用于消息演进）
     * 
     * 该版本号用于支持事件结构的向后兼容演进
     * 
     * @return 事件模式版本号
     */
    Integer getSchemaVersion();
}
