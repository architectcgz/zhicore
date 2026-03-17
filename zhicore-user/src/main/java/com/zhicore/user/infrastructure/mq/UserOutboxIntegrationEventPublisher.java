package com.zhicore.user.infrastructure.mq;

import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.integration.messaging.IntegrationEvent;
import com.zhicore.user.application.port.event.UserIntegrationEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户集成事件 Outbox 发布器。
 */
@Component
@RequiredArgsConstructor
public class UserOutboxIntegrationEventPublisher implements UserIntegrationEventPort {

    private final UserOutboxEventWriter userOutboxEventWriter;

    @Override
    public void publish(IntegrationEvent event) {
        if (event == null) {
            return;
        }

        userOutboxEventWriter.save(
                TopicConstants.TOPIC_USER_EVENTS,
                event.getTag(),
                String.valueOf(event.getAggregateId()),
                JsonUtils.toJson(event)
        );
    }
}
