package com.zhicore.notification.domain.model;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 通知聚合根。
 */
public class Notification {

    private static final String LEGACY_EVENT_CODE_SENTINEL = "__legacy__";
    private static final String LEGACY_EVENT_CODE_SENTINEL_V1 = "legacy.default";
    private static final int MAX_CONTENT_LENGTH = 100;

    private final Long id;
    private final Long recipientId;
    private final NotificationType type;
    private final OffsetDateTime createdAt;

    private String category;
    private String eventCode;
    private String metadata;
    private Long actorId;
    private String targetType;
    private Long targetId;
    private String content;
    private boolean read;
    private OffsetDateTime readAt;
    private String sourceEventId;
    private String groupKey;
    private String payloadJson;
    private NotificationImportance importance;

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
        this.createdAt = OffsetDateTime.now();
        this.importance = NotificationImportance.NORMAL;
    }

    private Notification(Long id,
                         Long recipientId,
                         NotificationType type,
                         String category,
                         String eventCode,
                         String metadata,
                         Long actorId,
                         String targetType,
                         Long targetId,
                         String sourceEventId,
                         String groupKey,
                         String payloadJson,
                         NotificationImportance importance,
                         String content,
                         boolean read,
                         OffsetDateTime readAt,
                         OffsetDateTime createdAt) {
        Assert.notNull(id, "通知ID不能为空");
        Assert.notNull(recipientId, "接收者ID不能为空");
        Assert.notNull(type, "通知类型不能为空");

        this.id = id;
        this.recipientId = recipientId;
        this.type = type;
        this.category = resolveCategory(type, category, eventCode);
        this.eventCode = resolveEventCode(type, eventCode);
        this.metadata = metadata;
        this.actorId = actorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.sourceEventId = sourceEventId;
        this.groupKey = groupKey;
        this.payloadJson = payloadJson;
        this.importance = importance != null ? importance : NotificationImportance.NORMAL;
        this.content = content;
        this.read = read;
        this.readAt = readAt;
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now();
    }

    public static Notification createLikeNotification(Long id,
                                                      Long recipientId,
                                                      Long actorId,
                                                      String targetType,
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

    public static Notification createCommentNotification(Long id,
                                                         Long recipientId,
                                                         Long actorId,
                                                         Long postId,
                                                         Long commentId,
                                                         String commentContent) {
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

    public static Notification createReplyNotification(Long id,
                                                       Long recipientId,
                                                       Long actorId,
                                                       Long commentId,
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

    public static Notification createFollowNotification(Long id, Long recipientId, Long actorId) {
        Assert.notNull(actorId, "触发者ID不能为空");
        Assert.isTrue(actorId > 0, "触发者ID必须为正数");

        Notification notification = new Notification(id, recipientId, NotificationType.FOLLOW);
        notification.actorId = actorId;
        notification.content = "关注了你";
        return notification;
    }

    public static Notification createSystemNotification(Long id, Long recipientId, String content) {
        Assert.hasText(content, "通知内容不能为空");

        Notification notification = new Notification(id, recipientId, NotificationType.SYSTEM);
        notification.content = truncate(content, MAX_CONTENT_LENGTH);
        return notification;
    }

    public static Notification createPostPublishedNotification(Long id,
                                                               Long recipientId,
                                                               Long authorId,
                                                               Long postId,
                                                               String groupKey,
                                                               String content) {
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

    public static Notification createPostPublishedDigestNotification(Long id,
                                                                     Long recipientId,
                                                                     String groupKey,
                                                                     String content) {
        Assert.hasText(groupKey, "分组键不能为空");
        Assert.hasText(content, "通知内容不能为空");

        Notification notification = new Notification(id, recipientId, NotificationType.POST_PUBLISHED_DIGEST);
        notification.targetType = "digest";
        notification.groupKey = groupKey;
        notification.content = truncate(content, MAX_CONTENT_LENGTH);
        return notification;
    }

    public static Notification reconstitute(Long id,
                                            Long recipientId,
                                            NotificationType type,
                                            Long actorId,
                                            String targetType,
                                            Long targetId,
                                            String content,
                                            boolean isRead,
                                            OffsetDateTime readAt,
                                            OffsetDateTime createdAt) {
        return new Notification(
                id, recipientId, type, null, null, null, actorId, targetType, targetId,
                null, null, null, null, content, isRead, readAt, createdAt
        );
    }

    public static Notification reconstitute(Long id,
                                            Long recipientId,
                                            NotificationType type,
                                            String category,
                                            String eventCode,
                                            String metadata,
                                            Long actorId,
                                            String targetType,
                                            Long targetId,
                                            String content,
                                            boolean isRead,
                                            OffsetDateTime readAt,
                                            OffsetDateTime createdAt) {
        return new Notification(
                id, recipientId, type, category, eventCode, metadata, actorId, targetType, targetId,
                null, null, null, null, content, isRead, readAt, createdAt
        );
    }

    public static Notification reconstitute(Long id,
                                            Long recipientId,
                                            NotificationType type,
                                            String category,
                                            String eventCode,
                                            String metadata,
                                            Long actorId,
                                            String targetType,
                                            Long targetId,
                                            String sourceEventId,
                                            String groupKey,
                                            String payloadJson,
                                            NotificationImportance importance,
                                            String content,
                                            boolean isRead,
                                            OffsetDateTime readAt,
                                            OffsetDateTime createdAt) {
        return new Notification(
                id, recipientId, type, category, eventCode, metadata, actorId, targetType, targetId,
                sourceEventId, groupKey, payloadJson, importance, content, isRead, readAt, createdAt
        );
    }

    public void markAsRead() {
        if (!read) {
            read = true;
            readAt = OffsetDateTime.now();
        }
    }

    public boolean isSameGroup(Notification other) {
        if (other == null || type != other.type) {
            return false;
        }
        if (groupKey != null || other.groupKey != null) {
            return Objects.equals(groupKey, other.groupKey);
        }
        if (type == NotificationType.FOLLOW) {
            return true;
        }
        return Objects.equals(targetType, other.targetType)
                && Objects.equals(targetId, other.targetId);
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public NotificationCategory getCategoryEnum() {
        return NotificationCategory.fromValue(category);
    }

    public String getEventCode() {
        return eventCode;
    }

    public String getMetadata() {
        return metadata;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getContent() {
        return content;
    }

    public boolean isRead() {
        return read;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public NotificationImportance getImportance() {
        return importance;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static String resolveCategory(NotificationType type, String category, String eventCode) {
        if (isLegacyEventCode(eventCode) && type != null) {
            return type.getCategory().name();
        }
        if (StringUtils.hasText(category)) {
            return NotificationCategory.fromValue(category).name();
        }
        return type != null ? type.getCategory().name() : NotificationCategory.SYSTEM.name();
    }

    private static String resolveEventCode(NotificationType type, String eventCode) {
        if (StringUtils.hasText(eventCode) && !isLegacyEventCode(eventCode)) {
            return eventCode.trim();
        }
        return type != null ? type.getEventCode() : NotificationType.SYSTEM.getEventCode();
    }

    private static boolean isLegacyEventCode(String eventCode) {
        if (!StringUtils.hasText(eventCode)) {
            return true;
        }
        String normalized = eventCode.trim();
        return LEGACY_EVENT_CODE_SENTINEL.equals(normalized)
                || LEGACY_EVENT_CODE_SENTINEL_V1.equals(normalized);
    }
}
