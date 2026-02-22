package com.zhicore.comment.infrastructure.mq;

import com.zhicore.api.event.DomainEvent;
import com.zhicore.api.event.comment.CommentCreatedEvent;
import com.zhicore.common.mq.DomainEventPublisher;
import com.zhicore.common.mq.TopicConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评论事件发布器
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * 发布评论事件
     */
    public void publish(DomainEvent event) {
        String topic = TopicConstants.TOPIC_COMMENT_EVENTS;
        String tag = event.getTag();
        
        domainEventPublisher.publish(topic, tag, event);
        log.info("Published comment event: topic={}, tag={}, eventId={}", 
                topic, tag, event.getEventId());
    }

    /**
     * 发布评论创建事件
     */
    public void publishCommentCreated(CommentCreatedEvent event) {
        publish(event);
    }
}
