package com.zhicore.notification.infrastructure.mq;

/**
 * 通知服务消费者组常量
 * 
 * 按照项目规范，消费者组常量定义在服务内部
 *
 * @author ZhiCore Team
 */
public final class NotificationConsumerGroups {

    private NotificationConsumerGroups() {
    }

    /**
     * 文章点赞通知消费者组
     */
    public static final String POST_LIKED_CONSUMER = "notification-post-liked-consumer";

    /**
     * 评论创建通知消费者组
     */
    public static final String COMMENT_CREATED_CONSUMER = "notification-comment-created-consumer";

    /**
     * 用户关注通知消费者组
     */
    public static final String USER_FOLLOWED_CONSUMER = "notification-user-followed-consumer";
}
