package com.zhicore.comment.application.port.event;

import com.zhicore.integration.messaging.IntegrationEvent;

/**
 * 评论跨服务集成事件发布端口。
 */
public interface CommentIntegrationEventPort {

    /**
     * 发布评论集成事件。
     */
    void publish(IntegrationEvent event);
}
