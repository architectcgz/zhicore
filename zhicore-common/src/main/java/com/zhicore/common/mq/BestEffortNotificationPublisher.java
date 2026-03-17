package com.zhicore.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Best-effort 通知发布器。
 *
 * <p>仅用于非关键、可丢失通知流量。关键业务链路禁止直接依赖本发布器，
 * 应改用 Outbox 或 {@link DomainEventPublisher} 的同步发送能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class BestEffortNotificationPublisher {

    private static final long DEFAULT_SEND_TIMEOUT_MILLIS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 异步发送普通通知。
     */
    public void publishAsync(String topic, String tag, Object notification) {
        String destination = MqEventMessageSupport.buildDestination(topic, tag);

        rocketMQTemplate.asyncSend(destination, MqEventMessageSupport.buildMessage(notification), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Best-effort notification published: topic={}, tag={}, msgId={}",
                        topic, tag, sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable exception) {
                log.error("Best-effort notification publish failed: topic={}, tag={}, error={}",
                        topic, tag, exception.getMessage(), exception);
            }
        });
    }

    /**
     * 异步发送顺序通知。
     */
    public void publishOrderlyAsync(String topic, String tag, Object notification, String hashKey) {
        String destination = MqEventMessageSupport.buildDestination(topic, tag);

        rocketMQTemplate.asyncSendOrderly(
                destination,
                MqEventMessageSupport.buildMessage(notification),
                hashKey,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.debug("Best-effort orderly notification published: topic={}, tag={}, hashKey={}, msgId={}",
                                topic, tag, hashKey, sendResult.getMsgId());
                    }

                    @Override
                    public void onException(Throwable exception) {
                        log.error("Best-effort orderly notification publish failed: topic={}, tag={}, hashKey={}, error={}",
                                topic, tag, hashKey, exception.getMessage(), exception);
                    }
                }
        );
    }

    /**
     * 异步发送延迟通知。
     */
    public void publishDelayedAsync(String topic, String tag, Object notification, int delayLevel) {
        String destination = MqEventMessageSupport.buildDestination(topic, tag);

        rocketMQTemplate.asyncSend(
                destination,
                MqEventMessageSupport.buildMessage(notification),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.debug("Best-effort delayed notification published: topic={}, tag={}, delayLevel={}, msgId={}",
                                topic, tag, delayLevel, sendResult.getMsgId());
                    }

                    @Override
                    public void onException(Throwable exception) {
                        log.error("Best-effort delayed notification publish failed: topic={}, tag={}, delayLevel={}, error={}",
                                topic, tag, delayLevel, exception.getMessage(), exception);
                    }
                },
                DEFAULT_SEND_TIMEOUT_MILLIS,
                delayLevel
        );
    }
}
