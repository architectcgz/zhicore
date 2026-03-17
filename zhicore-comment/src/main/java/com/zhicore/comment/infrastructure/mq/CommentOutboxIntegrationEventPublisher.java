package com.zhicore.comment.infrastructure.mq;

import com.zhicore.comment.application.port.event.CommentIntegrationEventPort;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 评论集成事件 outbox 发布器。
 */
@Component
@RequiredArgsConstructor
public class CommentOutboxIntegrationEventPublisher implements CommentIntegrationEventPort {

    private final CommentOutboxEventWriter commentOutboxEventWriter;

    @Override
    public void publish(IntegrationEvent event) {
        if (event == null) {
            return;
        }

        commentOutboxEventWriter.save(
                TopicConstants.TOPIC_COMMENT_EVENTS,
                event.getTag(),
                String.valueOf(event.getAggregateId()),
                JsonUtils.toJson(event)
        );
    }
}
