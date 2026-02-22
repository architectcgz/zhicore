package com.zhicore.content.infrastructure.mq;

import com.zhicore.api.event.DomainEvent;
import com.zhicore.common.mq.DomainEventPublisher;
import com.zhicore.common.mq.TopicConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 文章事件发布器
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class PostEventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    public PostEventPublisher() {
        this.domainEventPublisher = null;
        log.warn("DomainEventPublisher not available, events will not be published");
    }

    @Autowired(required = false)
    public PostEventPublisher(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
        if (domainEventPublisher == null) {
            log.warn("DomainEventPublisher not available, events will not be published");
        }
    }

    /**
     * 发布文章事件
     */
    public void publish(DomainEvent event) {
        if (domainEventPublisher == null) {
            log.debug("Event publisher not available, skipping event: {}", event.getClass().getSimpleName());
            return;
        }
        domainEventPublisher.publish(TopicConstants.TOPIC_POST_EVENTS, event.getTag(), event);
        log.debug("Post event published: type={}, eventId={}", 
                event.getClass().getSimpleName(), event.getEventId());
    }

    /**
     * 发布延迟事件（用于定时发布）
     *
     * @param event 事件
     * @param delayLevel 延迟级别（1-18）
     */
    public void publishDelayed(DomainEvent event, int delayLevel) {
        if (domainEventPublisher == null) {
            log.debug("Event publisher not available, skipping delayed event: {}", event.getClass().getSimpleName());
            return;
        }
        domainEventPublisher.publishDelayed(TopicConstants.TOPIC_POST_EVENTS, event.getTag(), event, delayLevel);
        log.debug("Delayed post event published: type={}, eventId={}, delayLevel={}", 
                event.getClass().getSimpleName(), event.getEventId(), delayLevel);
    }
}
