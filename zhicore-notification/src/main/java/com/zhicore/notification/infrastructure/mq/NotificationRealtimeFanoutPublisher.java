package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.BestEffortNotificationPublisher;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import com.zhicore.notification.infrastructure.mq.payload.CommentStreamFanoutEvent;
import com.zhicore.notification.infrastructure.mq.payload.UnreadCountFanoutEvent;
import com.zhicore.notification.infrastructure.mq.payload.UserNotificationFanoutEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 通知 realtime fanout 发布器。
 */
@Component
@RequiredArgsConstructor
public class NotificationRealtimeFanoutPublisher {

    private final BestEffortNotificationPublisher bestEffortNotificationPublisher;

    /**
     * 发布评论流提示 fanout 事件。
     */
    public void publishCommentStreamHint(String postId, CommentStreamHintPayload payload) {
        bestEffortNotificationPublisher.publishOrderlyAsync(
                TopicConstants.TOPIC_NOTIFICATION_EVENTS,
                TopicConstants.TAG_NOTIFICATION_REALTIME_COMMENT_STREAM,
                CommentStreamFanoutEvent.builder()
                        .eventId(payload.getEventId())
                        .postId(postId)
                        .payload(payload)
                        .build(),
                postId
        );
    }

    /**
     * 发布用户通知及未读数 fanout 事件。
     */
    public void publishUserNotification(String userId,
                                        AggregatedNotificationVO payload,
                                        int unreadCount) {
        String eventId = buildUserNotificationEventId(payload, unreadCount);
        bestEffortNotificationPublisher.publishOrderlyAsync(
                TopicConstants.TOPIC_NOTIFICATION_EVENTS,
                TopicConstants.TAG_NOTIFICATION_REALTIME_USER_NOTIFICATION,
                UserNotificationFanoutEvent.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .payload(payload)
                        .build(),
                userId
        );
        publishUnreadCount(userId, unreadCount, eventId);
    }

    /**
     * 仅发布未读数 fanout 事件。
     */
    public void publishUnreadCount(String userId, int unreadCount) {
        publishUnreadCount(userId, unreadCount, "unread:%s:%d".formatted(userId, unreadCount));
    }

    private void publishUnreadCount(String userId, int unreadCount, String sourceEventId) {
        bestEffortNotificationPublisher.publishOrderlyAsync(
                TopicConstants.TOPIC_NOTIFICATION_EVENTS,
                TopicConstants.TAG_NOTIFICATION_REALTIME_UNREAD_COUNT,
                UnreadCountFanoutEvent.builder()
                        .eventId(sourceEventId + ":unread")
                        .userId(userId)
                        .unreadCount(unreadCount)
                        .build(),
                userId
        );
    }

    private String buildUserNotificationEventId(AggregatedNotificationVO payload, int unreadCount) {
        String type = payload.getType() == null ? "unknown" : payload.getType().name().toLowerCase();
        String targetType = payload.getTargetType() == null ? "unknown" : payload.getTargetType().toLowerCase();
        String targetId = Objects.toString(payload.getTargetId(), "unknown");
        String latestTime = Objects.toString(payload.getLatestTime(), "unknown");
        return "%s:%s:%s:%s:%d".formatted(type, targetType, targetId, latestTime, unreadCount);
    }
}
