package com.zhicore.user.infrastructure.mq;

import com.zhicore.api.event.DomainEvent;
import com.zhicore.api.event.user.UserFollowedEvent;
import com.zhicore.api.event.user.UserRegisteredEvent;
import com.zhicore.common.mq.DomainEventPublisher;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.user.domain.event.UserProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 用户事件发布器 - RocketMQ 实现
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class UserEventPublisher implements EventPublisher {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * 发布用户领域事件
     *
     * @param event 领域事件
     */
    @Override
    public void publish(DomainEvent event) {
        String tag = resolveTag(event);
        domainEventPublisher.publish(TopicConstants.TOPIC_USER_EVENTS, tag, event);
        log.debug("Published user event: type={}, tag={}, eventId={}", 
                event.getClass().getSimpleName(), tag, event.getEventId());
    }

    /**
     * 根据事件类型解析 Tag
     */
    private String resolveTag(DomainEvent event) {
        if (event instanceof UserRegisteredEvent) {
            return TopicConstants.TAG_USER_REGISTERED;
        } else if (event instanceof UserFollowedEvent) {
            return TopicConstants.TAG_USER_FOLLOWED;
        } else if (event instanceof UserProfileUpdatedEvent) {
            return TopicConstants.TAG_USER_PROFILE_UPDATED;
        }
        return "default";
    }
}
