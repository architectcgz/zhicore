package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.notification.infrastructure.mq.payload.UserNotificationFanoutEvent;
import com.zhicore.notification.infrastructure.push.WebSocketNotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 用户通知 fanout 消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_NOTIFICATION_EVENTS,
        selectorExpression = TopicConstants.TAG_NOTIFICATION_REALTIME_USER_NOTIFICATION,
        consumerGroup = NotificationRealtimeConsumerGroups.USER_NOTIFICATION_FANOUT_CONSUMER,
        messageModel = MessageModel.BROADCASTING
)
public class UserNotificationFanoutConsumer implements RocketMQListener<String> {

    private final WebSocketNotificationHandler webSocketNotificationHandler;

    @Override
    public void onMessage(String message) {
        try {
            UserNotificationFanoutEvent event = JsonUtils.fromJson(message, UserNotificationFanoutEvent.class);
            webSocketNotificationHandler.sendNotification(event.getUserId(), event.getPayload());
        } catch (Exception e) {
            log.warn("处理用户通知 fanout 事件失败: message={}, error={}", message, e.getMessage(), e);
        }
    }
}
