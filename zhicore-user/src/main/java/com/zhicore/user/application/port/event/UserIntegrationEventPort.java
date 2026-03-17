package com.zhicore.user.application.port.event;

import com.zhicore.integration.messaging.IntegrationEvent;

/**
 * 用户跨服务集成事件发布端口。
 */
public interface UserIntegrationEventPort {

    /**
     * 发布用户集成事件。
     */
    void publish(IntegrationEvent event);
}
