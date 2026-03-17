package com.zhicore.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 可靠 MQ 事件发布器。
 *
 * <p>关键业务链路应优先使用 Outbox。只有在无法落 durable queue 且必须同步感知
 * RocketMQ 投递结果时，才应直接依赖本发布器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class DomainEventPublisher {

    private static final long DEFAULT_SEND_TIMEOUT_MILLIS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送普通消息（同步）
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     * @return 发送结果
     */
    public SendResult publishSync(String topic, String tag, Object event) {
        String destination = MqEventMessageSupport.buildDestination(topic, tag);

        SendResult result = rocketMQTemplate.syncSend(destination, MqEventMessageSupport.buildMessage(event));
        log.debug("Event published synchronously: topic={}, tag={}, msgId={}",
                topic, tag, result.getMsgId());
        return result;
    }

    /**
     * 发送顺序消息（同步）。
     *
     * <p>相同 hashKey 的消息会被发送到同一个队列，保证顺序。
     * 适用于 Outbox 派发等需要明确感知 RocketMQ 投递结果的场景。
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     * @param hashKey 用于分区的 key（如用户ID、订单ID）
     * @return 发送结果
     */
    public SendResult publishOrderlySync(String topic, String tag, Object event, String hashKey) {
        String destination = MqEventMessageSupport.buildDestination(topic, tag);

        SendResult result = rocketMQTemplate.syncSendOrderly(
                destination,
                MqEventMessageSupport.buildMessage(event),
                hashKey
        );
        log.debug("Orderly event published synchronously: topic={}, tag={}, hashKey={}, msgId={}",
                topic, tag, hashKey, result.getMsgId());
        return result;
    }

    /**
     * 发送延迟消息
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     * @param delayLevel 延迟级别（1-18）
     *                   1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * @return 发送结果
     */
    public SendResult publishDelayedSync(String topic, String tag, Object event, int delayLevel) {
        String destination = MqEventMessageSupport.buildDestination(topic, tag);

        SendResult result = rocketMQTemplate.syncSend(
                destination,
                MqEventMessageSupport.buildMessage(event),
                DEFAULT_SEND_TIMEOUT_MILLIS,
                delayLevel
        );
        log.debug("Delayed event published synchronously: topic={}, tag={}, delayLevel={}, msgId={}",
                topic, tag, delayLevel, result.getMsgId());
        return result;
    }
}
