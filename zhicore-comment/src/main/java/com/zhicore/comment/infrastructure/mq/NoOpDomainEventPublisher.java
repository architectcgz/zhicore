package com.zhicore.comment.infrastructure.mq;

import com.zhicore.common.mq.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 空操作领域事件发布器 - 当 RocketMQ 不可用时使用
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@ConditionalOnMissingBean(RocketMQTemplate.class)
public class NoOpDomainEventPublisher extends DomainEventPublisher {

    public NoOpDomainEventPublisher() {
        super(null);
    }

    @Override
    public void publish(String topic, String tag, Object event) {
        log.warn("RocketMQ not available, event not published: topic={}, tag={}, type={}", 
                topic, tag, event.getClass().getSimpleName());
    }

    @Override
    public SendResult publishSync(String topic, String tag, Object event) {
        log.warn("RocketMQ not available, sync event not published: topic={}, tag={}, type={}", 
                topic, tag, event.getClass().getSimpleName());
        return null;
    }

    @Override
    public void publishOrderly(String topic, String tag, Object event, String hashKey) {
        log.warn("RocketMQ not available, orderly event not published: topic={}, tag={}, hashKey={}, type={}", 
                topic, tag, hashKey, event.getClass().getSimpleName());
    }

    @Override
    public void publishDelayed(String topic, String tag, Object event, int delayLevel) {
        log.warn("RocketMQ not available, delayed event not published: topic={}, tag={}, delayLevel={}, type={}", 
                topic, tag, delayLevel, event.getClass().getSimpleName());
    }
}
