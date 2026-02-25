package com.zhicore.common.mq;

import com.zhicore.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器
 * 
 * 支持普通消息、顺序消息、事务消息、延迟消息
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class DomainEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送普通消息（异步）
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     */
    public void publish(String topic, String tag, Object event) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(event);

        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Event published successfully: topic={}, tag={}, msgId={}",
                        topic, tag, sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to publish event: topic={}, tag={}, error={}",
                        topic, tag, e.getMessage(), e);
            }
        });
    }

    /**
     * 发送普通消息（同步）
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     * @return 发送结果
     */
    public SendResult publishSync(String topic, String tag, Object event) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(event);

        SendResult result = rocketMQTemplate.syncSend(destination, message);
        log.debug("Event published synchronously: topic={}, tag={}, msgId={}",
                topic, tag, result.getMsgId());
        return result;
    }

    /**
     * 发送顺序消息
     * 
     * 相同 hashKey 的消息会被发送到同一个队列，保证顺序
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     * @param hashKey 用于分区的 key（如用户ID、订单ID）
     */
    public void publishOrderly(String topic, String tag, Object event, String hashKey) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(event);

        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Orderly event published: topic={}, tag={}, hashKey={}, msgId={}",
                        topic, tag, hashKey, sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to publish orderly event: topic={}, tag={}, hashKey={}, error={}",
                        topic, tag, hashKey, e.getMessage(), e);
            }
        });
    }

    /**
     * 发送延迟消息
     *
     * @param topic 主题
     * @param tag 标签
     * @param event 事件对象
     * @param delayLevel 延迟级别（1-18）
     *                   1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    public void publishDelayed(String topic, String tag, Object event, int delayLevel) {
        String destination = buildDestination(topic, tag);
        Message<String> message = buildMessage(event);

        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("Delayed event published: topic={}, tag={}, delayLevel={}, msgId={}",
                        topic, tag, delayLevel, sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Failed to publish delayed event: topic={}, tag={}, delayLevel={}, error={}",
                        topic, tag, delayLevel, e.getMessage(), e);
            }
        }, 3000, delayLevel);
    }

    /**
     * 构建目标地址
     */
    private String buildDestination(String topic, String tag) {
        return topic + ":" + tag;
    }

    /**
     * 构建消息
     */
    private Message<String> buildMessage(Object event) {
        String payload = JsonUtils.toJson(event);
        return MessageBuilder.withPayload(payload)
                .setHeader("eventType", event.getClass().getSimpleName())
                .build();
    }
}
