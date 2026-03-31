package com.zhicore.notification.domain.model;

import com.zhicore.common.util.JsonUtils;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

/**
 * 通知聚合根（充血模型）
 * 
 * 设计原则：
 * 1. 私有构造函数 + 工厂方法
 * 2. 领域行为封装业务规则
 * 3. 不变量在构造时和方法内保证
 *
 * @author ZhiCore Team
 */
@Getter
public class Notification {

    private static final String LEGACY_EVENT_CODE_SENTINEL = "__legacy__";
    private static final String LEGACY_EVENT_CODE_SENTINEL_V1 = "legacy.default";

    /**
     * 通知ID（雪花ID）
     */
    private final Long id;

    /**
     * 接收者ID
     */
    private final Long recipientId;

    /**
     * 通知类型
     */
    private final NotificationType type;

    /**
     * 平台化分类（第一阶段与类型保持一一对应，后续可独立扩展）。
     */
    private String category;

    /**
     * 平台化事件编码（用于后续模板路由与策略扩展）。
     */
    private String eventCode;

    /**
     * 平台化扩展元数据（JSON 字符串）。
     */
    private String metadata;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 通知分类
     */
    private final NotificationCategory category;

    /**
     * 触发者ID
     */
    private Long actorId;

    /**
     * 目标类型（post/comment）
     */
    private String targetType;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 是否已读
     */
    private boolean isRead;

    /**
     * 已读时间
     */
    private LocalDateTime readAt;

    /**
     * 事件源ID
     */
    private String sourceEventId;

    /**
     * 聚合分组键
     */
    private String groupKey;

    /**
     * 扩展载荷
     */
    private String payloadJson;

    /**
     * 重要程度
     */
    private NotificationImportance importance;

    /**
     * 内容最大长度
     */
    private static final int MAX_CONTENT_LENGTH = 100;

    /**
     * 私有构造函数
     */
    private Notification(Long id, Long recipientId, NotificationType type) {
        Assert.notNull(id, "通知ID不能为空");
        Assert.isTrue(id > 0, "通知ID必须为正数");
        Assert.notNull(recipientId, "接收者ID不能为空");
        Assert.isTrue(recipientId > 0, "接收者ID必须为正数");
        Assert.notNull(type, "通知类型不能为空");

        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
        this.category = defaultCategoryFor(type);
        this.importance = NotificationImportance.NORMAL;
    }

    /**
     * 私有构造函数（用于从持久化恢复）
     */
    private Notification(Long id, Long recipientId, NotificationType type,
                         NotificationCategory category,
                         Long actorId, String targetType, Long targetId,
                         String sourceEventId, String groupKey, String payloadJson,
                         NotificationImportance importance,
                         String content, boolean isRead, LocalDateTime readAt,
                         LocalDateTime createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.category = category != null ? category : defaultCategoryFor(type);
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.sourceEventId = sourceEventId;
        this.groupKey = groupKey;
        this.payloadJson = payloadJson;
        this.importance = importance != null ? importance : NotificationImportance.NORMAL;
        this.content = content;
        this.isRead = isRead;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建点赞通知
     *
     * @param id 通知ID
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @param targetType 目标类型（post/comment）
     * @param targetId 目标ID
     * @return 通知实例
     */
    public static Notification createLikeNotification(Long id, Long recipientId,
                                                       Long actorId, String targetType,
                                                       Long targetId) {
        Assert.notNull(actorId, "触发者ID不能为空");
        Assert.isTrue(actorId > 0, "触发者ID必须为正数");
        Assert.hasText(targetType, "目标类型不能为空");
        Assert.notNull(targetId, "目标ID不能为空");
        Assert.isTrue(targetId > 0, "目标ID必须为正数");

        Notification notification = new Notification(id, recipientId, NotificationType.LIKE);
        notification.actorId = actorId;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.content = "赞了你的" + ("post".equals(targetType) ? "文章" : "评论");
        return notification;
    }

    /**
     * 创建评论通知
     *
     * @param id 通知ID
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @param postId 文章ID
     * @param commentId 评论ID
     * @param commentContent 评论内容
     * @return 通知实例
     */
    public static Notification createCommentNotification(Long id, Long recipientId,
                                                          Long actorId, Long postId,
                                                          Long commentId, String commentContent) {
        Assert.notNull(actorId, "触发者ID不能为空");
        Assert.isTrue(actorId > 0, "触发者ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.isTrue(postId > 0, "文章ID必须为正数");

        Notification notification = new Notification(id, recipientId, NotificationType.COMMENT);
        notification.actorId = actorId;
        notification.targetType = "post";
        notification.targetId = postId;
        notification.content = truncate(commentContent, MAX_CONTENT_LENGTH);
        return notification;
    }

    /**
     * 创建回复通知
     *
     * @param id 通知ID
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @param commentId 评论ID
     * @param replyContent 回复内容
     * @return 通知实例
     */
    public static Notification createReplyNotification(Long id, Long recipientId,
                                                        Long actorId, Long commentId,
                                                        String replyContent) {
        Assert.notNull(actorId, "触发者ID不能为空");
        Assert.isTrue(actorId > 0, "触发者ID必须为正数");
        Assert.notNull(commentId, "评论ID不能为空");
        Assert.isTrue(commentId > 0, "评论ID必须为正数");

        Notification notification = new Notification(id, recipientId, NotificationType.REPLY);
        notification.actorId = actorId;
        notification.targetType = "comment";
        notification.targetId = commentId;
        notification.content = truncate(replyContent, MAX_CONTENT_LENGTH);
        return notification;
    }

    /**
     * 创建关注通知
     *
     * @param id 通知ID
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @return 通知实例
     */
    public static Notification createFollowNotification(Long id, Long recipientId,
                                                         Long actorId) {
        Assert.notNull(actorId, "触发者ID不能为空");
        Assert.isTrue(actorId > 0, "触发者ID必须为正数");

        Notification notification = new Notification(id, recipientId, NotificationType.FOLLOW);
        notification.actorId = actorId;
        notification.content = "关注了你";
        return notification;
    }

    /**
     * 创建系统通知
     *
     * @param id 通知ID
     * @param recipientId 接收者ID
     * @param content 通知内容
     * @return 通知实例
     */
    public static Notification createSystemNotification(Long id, Long recipientId,
                                                         String content) {
        Assert.hasText(content, "通知内容不能为空");

        Notification notification = new Notification(id, recipientId, NotificationType.SYSTEM);
        notification.content = truncate(content, MAX_CONTENT_LENGTH);
        return notification;
    }

    /**
     * 创建关注作者发文通知
     */
    public static Notification createPostPublishedNotification(Long id, Long recipientId,
                                                               Long authorId, Long postId,
                                                               String groupKey, String content) {
        Assert.notNull(authorId, "作者ID不能为空");
        Assert.isTrue(authorId > 0, "作者ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.isTrue(postId > 0, "文章ID必须为正数");
        Assert.hasText(groupKey, "分组键不能为空");
        Assert.hasText(content, "通知内容不能为空");

        Notification notification = new Notification(id, recipientId, NotificationType.POST_PUBLISHED_BY_FOLLOWING);
        notification.actorId = authorId;
        notification.targetType = "post";
        notification.targetId = postId;
        notification.groupKey = groupKey;
        notification.content = truncate(content, MAX_CONTENT_LENGTH);
        return notification;
    }

    /**
     * 创建关注作者发文摘要通知
     */
    public static Notification createPostPublishedDigestNotification(Long id, Long recipientId,
                                                                     String groupKey, String content) {
        Assert.hasText(groupKey, "分组键不能为空");
        Assert.hasText(content, "通知内容不能为空");

        Notification notification = new Notification(id, recipientId, NotificationType.POST_PUBLISHED_DIGEST);
        notification.targetType = "digest";
        notification.groupKey = groupKey;
        notification.content = truncate(content, MAX_CONTENT_LENGTH);
        return notification;
    }

    /**
     * 从持久化恢复通知
     *
     * @return 通知实例
     */
    public static Notification reconstitute(Long id, Long recipientId, NotificationType type,
                                            Long actorId, String targetType, Long targetId,
                                            String content, boolean isRead, LocalDateTime readAt,
                                            LocalDateTime createdAt) {
        return new Notification(id, recipientId, type, null, actorId, targetType, targetId,
                null, null, null, null, content, isRead, readAt, createdAt);
    }

    /**
     * 从持久化恢复通知（含扩展元数据）
     */
    public static Notification reconstitute(Long id, Long recipientId, NotificationType type,
                                            NotificationCategory category,
                                            Long actorId, String targetType, Long targetId,
                                            String sourceEventId, String groupKey, String payloadJson,
                                            NotificationImportance importance,
                                            String content, boolean isRead, LocalDateTime readAt,
                                            LocalDateTime createdAt) {
        return new Notification(id, recipientId, type, category, actorId, targetType, targetId,
                sourceEventId, groupKey, payloadJson, importance, content, isRead, readAt, createdAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 标记为已读
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = OffsetDateTime.now();
        }
    }

    /**
     * 检查是否为同一聚合组
     * 用于通知聚合判断
     *
     * @param other 另一个通知
     * @return 是否为同一聚合组
     */
    public boolean isSameGroup(Notification other) {
        if (other == null) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        if (this.groupKey != null || other.groupKey != null) {
            return java.util.Objects.equals(this.groupKey, other.groupKey);
        }
        // 关注通知不按目标聚合
        if (this.type == NotificationType.FOLLOW) {
            return true;
        }
        // 其他类型按目标聚合
        return java.util.Objects.equals(this.targetType, other.targetType)
                && java.util.Objects.equals(this.targetId, other.targetId);
    }

    // ==================== 私有方法 ====================

    /**
     * 截断内容
     */
    private static String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private static NotificationCategory defaultCategoryFor(NotificationType type) {
        if (type == null) {
            return NotificationCategory.SYSTEM;
        }
        return switch (type) {
            case POST_LIKED, POST_COMMENTED, COMMENT_REPLIED, POST_PUBLISHED_BY_FOLLOWING,
                    POST_PUBLISHED_DIGEST, LIKE, COMMENT, REPLY -> NotificationCategory.CONTENT;
            case USER_FOLLOWED, FOLLOW -> NotificationCategory.SOCIAL;
            case SECURITY_ALERT -> NotificationCategory.SECURITY;
            case SYSTEM_ANNOUNCEMENT, SYSTEM -> NotificationCategory.SYSTEM;
        };
    }
}
