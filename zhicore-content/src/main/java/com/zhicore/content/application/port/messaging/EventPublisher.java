package com.zhicore.content.application.port.messaging;

import com.zhicore.content.domain.event.DomainEvent;

import java.util.List;

/**
 * 事件发布器端口接口
 * 
 * 定义内容服务内部事件发布契约，由基础设施层实现。
 * 用于将本地异步任务持久化到事件表，驱动 Mongo/tag_stats 等读模型更新。
 * 
 * @author ZhiCore Team
 */
public interface EventPublisher {
    
    /**
     * 发布单个事件
     * 
     * @param event 内部事件
     */
    void publish(DomainEvent event);
    
    /**
     * 批量发布事件
     * 
     * @param events 内部事件列表
     */
    void publishBatch(List<DomainEvent> events);
}
