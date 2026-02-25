package com.zhicore.content.application.port.messaging;

import com.zhicore.integration.messaging.IntegrationEvent;

import java.util.List;

/**
 * 集成事件发布器端口接口
 * 
 * 定义集成事件发布的契约，由基础设施层实现（使用 Outbox 模式）。
 * 用于发布集成事件，实现跨服务通信和最终一致性。
 * 
 * 实现类应使用 Outbox 模式：
 * 1. 事务内写入 Outbox 表
 * 2. 事务后由后台投递器异步发送到 RocketMQ
 * 
 * @author ZhiCore Team
 */
public interface IntegrationEventPublisher {
    
    /**
     * 发布单个集成事件（事务内写 Outbox 表）
     * 
     * @param event 集成事件
     */
    void publish(IntegrationEvent event);
    
    /**
     * 批量发布集成事件（事务内写 Outbox 表）
     * 
     * @param events 集成事件列表
     */
    void publishBatch(List<IntegrationEvent> events);
}
