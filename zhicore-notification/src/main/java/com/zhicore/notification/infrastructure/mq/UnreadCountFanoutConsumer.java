package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.notification.infrastructure.mq.payload.UnreadCountFanoutEvent;
import com.zhicore.notification.infrastructure.push.WebSocketNotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 未读数 fanout 消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_NOTIFICATION_EVENTS,
        selectorExpression = TopicConstants.TAG_NOTIFICATION_REALTIME_UNREAD_COUNT,
        consumerGroup = NotificationRealtimeConsumerGroups.UNREAD_COUNT_FANOUT_CONSUMER,
        messageModel = MessageModel.BROADCASTING
)
public class UnreadCountFanoutConsumer implements RocketMQListener<String> {

    private final WebSocketNotificationHandler webSocketNotificationHandler;

    @Override
    public void onMessage(String message) {
        try {
            UnreadCountFanoutEvent event = JsonUtils.fromJson(message, UnreadCountFanoutEvent.class);
            webSocketNotificationHandler.sendUnreadCount(event.getUserId(), event.getUnreadCount());
        } catch (Exception e) {
            log.warn("处理未读数 fanout 事件失败: message={}, error={}", message, e.getMessage(), e);
        }
    }
}
