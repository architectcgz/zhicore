package com.zhicore.common.mq;

/**
 * RocketMQ Topic 常量定义
 *
 * @author ZhiCore Team
 */
public final class TopicConstants {

    private TopicConstants() {
    }

    // ==================== Topics ====================

    /**
     * 文章相关事件 Topic
     */
    public static final String TOPIC_POST_EVENTS = "ZhiCore-post-events";

    /**
     * 用户相关事件 Topic
     */
    public static final String TOPIC_USER_EVENTS = "ZhiCore-user-events";

    /**
     * 评论相关事件 Topic
     */
    public static final String TOPIC_COMMENT_EVENTS = "ZhiCore-comment-events";

    /**
     * 消息相关事件 Topic
     */
    public static final String TOPIC_MESSAGE_EVENTS = "ZhiCore-message-events";

    /**
     * 通知相关事件 Topic
     */
    public static final String TOPIC_NOTIFICATION_EVENTS = "ZhiCore-notification-events";

    // ==================== Tags ====================

    /**
     * 文章发布
     */
    public static final String TAG_POST_PUBLISHED = "published";

    /**
     * 文章更新
     */
    public static final String TAG_POST_UPDATED = "updated";

    /**
     * 文章删除
     */
    public static final String TAG_POST_DELETED = "deleted";
    
    /**
     * 文章标签更新
     */
    public static final String TAG_POST_TAGS_UPDATED = "tags-updated";

    /**
     * 文章点赞
     */
    public static final String TAG_POST_LIKED = "liked";

    /**
     * 文章取消点赞
     */
    public static final String TAG_POST_UNLIKED = "unliked";

    /**
     * 文章收藏
     */
    public static final String TAG_POST_FAVORITED = "favorited";

    /**
     * 文章浏览
     */
    public static final String TAG_POST_VIEWED = "viewed";

    /**
     * 用户注册
     */
    public static final String TAG_USER_REGISTERED = "registered";

    /**
     * 用户关注
     */
    public static final String TAG_USER_FOLLOWED = "followed";

    /**
     * 用户取消关注
     */
    public static final String TAG_USER_UNFOLLOWED = "unfollowed";

    /**
     * 用户资料更新
     */
    public static final String TAG_USER_PROFILE_UPDATED = "profile-updated";

    /**
     * 评论创建
     */
    public static final String TAG_COMMENT_CREATED = "created";

    /**
     * 评论删除
     */
    public static final String TAG_COMMENT_DELETED = "deleted";

    /**
     * 评论点赞
     */
    public static final String TAG_COMMENT_LIKED = "liked";

    /**
     * 私信发送
     */
    public static final String TAG_MESSAGE_SENT = "sent";

    /**
     * 私信已读
     */
    public static final String TAG_MESSAGE_READ = "read";

    // ==================== Consumer Groups ====================

    /**
     * 搜索服务消费者组
     */
    public static final String GROUP_SEARCH_CONSUMER = "search-consumer-group";

    /**
     * 通知服务消费者组
     */
    public static final String GROUP_NOTIFICATION_CONSUMER = "notification-consumer-group";

    /**
     * 排行榜服务消费者组
     */
    public static final String GROUP_RANKING_CONSUMER = "ranking-consumer-group";

    /**
     * 统计服务消费者组
     */
    public static final String GROUP_STATS_CONSUMER = "stats-consumer-group";
}
