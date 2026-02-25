package com.zhicore.user.infrastructure.mq;

import com.zhicore.api.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 空操作事件发布器 - 当 RocketMQ 不可用时使用
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@ConditionalOnMissingBean(RocketMQTemplate.class)
public class NoOpEventPublisher implements EventPublisher {

    @Override
    public void publish(DomainEvent event) {
        log.warn("RocketMQ not available, event not published: type={}, eventId={}", 
                event.getClass().getSimpleName(), event.getEventId());
    }
}
