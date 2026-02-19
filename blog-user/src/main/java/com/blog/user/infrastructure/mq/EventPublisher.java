package com.blog.user.infrastructure.mq;

import com.blog.api.event.DomainEvent;

/**
 * 事件发布器接口
 *
 * @author Blog Team
 */
public interface EventPublisher {

    /**
     * 发布领域事件
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);
}
