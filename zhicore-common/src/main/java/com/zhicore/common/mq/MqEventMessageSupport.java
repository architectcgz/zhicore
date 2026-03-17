package com.zhicore.common.mq;

import com.zhicore.common.util.JsonUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * MQ 事件消息构建工具。
 */
final class MqEventMessageSupport {

    private MqEventMessageSupport() {
    }

    static String buildDestination(String topic, String tag) {
        return topic + ":" + tag;
    }

    static Message<String> buildMessage(Object event) {
        String payload = JsonUtils.toJson(event);
        return MessageBuilder.withPayload(payload)
                .setHeader("eventType", event.getClass().getSimpleName())
                .build();
    }
}
