package com.zhicore.notification.infrastructure.mq;

/**
 * 通知服务 realtime fanout 消费者组常量。
 */
public final class NotificationRealtimeConsumerGroups {

    private NotificationRealtimeConsumerGroups() {
    }

    /**
     * 评论流 fanout 消费者组。
     */
    public static final String COMMENT_STREAM_FANOUT_CONSUMER =
            "notification-realtime-comment-stream-fanout-consumer";

    /**
     * 用户通知 fanout 消费者组。
     */
    public static final String USER_NOTIFICATION_FANOUT_CONSUMER =
            "notification-realtime-user-notification-fanout-consumer";

    /**
     * 未读数 fanout 消费者组。
     */
    public static final String UNREAD_COUNT_FANOUT_CONSUMER =
            "notification-realtime-unread-count-fanout-consumer";
}
