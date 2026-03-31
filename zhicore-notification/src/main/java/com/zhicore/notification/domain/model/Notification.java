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
    private final OffsetDateTime createdAt;

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
    private OffsetDateTime readAt;

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
        this.category = type.getCategory().name();
        this.eventCode = type.getEventCode();
        this.metadata = null;
        this.isRead = false;
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * 私有构造函数（用于从持久化恢复）
     */
    private Notification(Long id, Long recipientId, NotificationType type,
                         String category, String eventCode, String metadata,
                         Long actorId, String targetType, Long targetId,
                         String content, boolean isRead, OffsetDateTime readAt,
                         OffsetDateTime createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.category = resolveCategory(type, category, eventCode);
        this.eventCode = resolveEventCode(type, eventCode);
        this.metadata = metadata;
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
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
     * 创建关注作者发布作品通知。
     */
    public static Notification createPostPublishedNotification(Long id,
                                                               Long recipientId,
                                                               Long authorId,
                                                               Long postId,
                                                               Long campaignId) {
        Assert.notNull(authorId, "作者ID不能为空");
        Assert.isTrue(authorId > 0, "作者ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.isTrue(postId > 0, "文章ID必须为正数");

        Notification notification = new Notification(id, recipientId, NotificationType.POST_PUBLISHED);
        notification.actorId = authorId;
        notification.targetType = "post";
        notification.targetId = postId;
        notification.content = "你关注的作者发布了新作品";
        notification.metadata = JsonUtils.toJson(java.util.Map.of(
                "campaignId", campaignId,
                "postId", postId
        ));
        return notification;
    }

    /**
     * 从持久化恢复通知
     *
     * @return 通知实例
     */
    public static Notification reconstitute(Long id, Long recipientId, NotificationType type,
                                            Long actorId, String targetType, Long targetId,
                                            String content, boolean isRead, OffsetDateTime readAt,
                                            OffsetDateTime createdAt) {
        return new Notification(id, recipientId, type, null, null, null,
                actorId, targetType, targetId,
                content, isRead, readAt, createdAt);
    }

    /**
     * 从持久化恢复通知（扩展元数据版本）。
     */
    public static Notification reconstitute(Long id, Long recipientId, NotificationType type,
                                            String category, String eventCode, String metadata,
                                            Long actorId, String targetType, Long targetId,
                                            String content, boolean isRead, OffsetDateTime readAt,
                                            OffsetDateTime createdAt) {
        return new Notification(id, recipientId, type, category, eventCode, metadata,
                actorId, targetType, targetId,
                content, isRead, readAt, createdAt);
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

    private static String resolveCategory(NotificationType type, String category, String eventCode) {
        if (!StringUtils.hasText(category) || isLegacyMetadata(eventCode)) {
            return type.getCategory().name();
        }
        return category;
    }

    private static String resolveEventCode(NotificationType type, String eventCode) {
        if (isLegacyMetadata(eventCode)) {
            return type.getEventCode();
        }
        return eventCode;
    }

    private static boolean isLegacyMetadata(String eventCode) {
        if (!StringUtils.hasText(eventCode)) {
            return true;
        }
        String normalized = eventCode.trim().toLowerCase();
        return LEGACY_EVENT_CODE_SENTINEL.equals(normalized)
                || LEGACY_EVENT_CODE_SENTINEL_V1.equals(normalized);
    }
}
