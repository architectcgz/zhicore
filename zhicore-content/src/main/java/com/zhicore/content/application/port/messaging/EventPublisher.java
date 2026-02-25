package com.zhicore.content.application.port.messaging;

import com.zhicore.content.domain.event.DomainEvent;

import java.util.List;

/**
 * 事件发布器端口接口
 * 
 * 定义领域事件发布的契约，由基础设施层实现（如 RocketMQ）。
 * 用于发布领域事件，实现事件驱动架构和最终一致性。
 * 
 * @author ZhiCore Team
 */
public interface EventPublisher {
    
    /**
     * 发布单个事件
     * 
     * @param event 领域事件
     */
    void publish(DomainEvent event);
    
    /**
     * 批量发布事件
     * 
     * @param events 领域事件列表
     */
    void publishBatch(List<DomainEvent> events);
}
