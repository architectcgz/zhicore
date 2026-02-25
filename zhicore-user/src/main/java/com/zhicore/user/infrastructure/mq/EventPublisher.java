package com.zhicore.user.infrastructure.mq;

import com.zhicore.api.event.DomainEvent;

/**
 * 事件发布器接口
 *
 * @author ZhiCore Team
 */
public interface EventPublisher {

    /**
     * 发布领域事件
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);
}
