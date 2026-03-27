package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.notification.infrastructure.mq.payload.CommentStreamFanoutEvent;
import com.zhicore.notification.infrastructure.push.WebSocketNotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 评论流 fanout 消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_NOTIFICATION_EVENTS,
        selectorExpression = TopicConstants.TAG_NOTIFICATION_REALTIME_COMMENT_STREAM,
        consumerGroup = NotificationRealtimeConsumerGroups.COMMENT_STREAM_FANOUT_CONSUMER,
        messageModel = MessageModel.BROADCASTING
)
public class CommentStreamFanoutConsumer implements RocketMQListener<String> {

    private final WebSocketNotificationHandler webSocketNotificationHandler;

    @Override
    public void onMessage(String message) {
        try {
            CommentStreamFanoutEvent event = JsonUtils.fromJson(message, CommentStreamFanoutEvent.class);
            webSocketNotificationHandler.sendCommentStreamHint(event.getPostId(), event.getPayload());
        } catch (Exception e) {
            log.warn("处理评论流 fanout 事件失败: message={}, error={}", message, e.getMessage(), e);
        }
    }
}
