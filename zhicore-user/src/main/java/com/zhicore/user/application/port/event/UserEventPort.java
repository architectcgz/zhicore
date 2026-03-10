package com.zhicore.user.application.port.event;

import com.zhicore.api.event.DomainEvent;

/**
 * 用户领域事件发布端口
 */
public interface UserEventPort {

    /**
     * 发布用户领域事件
     */
    void publish(DomainEvent event);
}
