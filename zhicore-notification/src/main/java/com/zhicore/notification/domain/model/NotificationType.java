package com.zhicore.notification.domain.model;

/**
 * 通知类型枚举。
 */
public enum NotificationType {

    POST_LIKED(0, "文章点赞", NotificationCategory.INTERACTION, "interaction.like"),
    POST_COMMENTED(1, "文章评论", NotificationCategory.INTERACTION, "interaction.comment"),
    USER_FOLLOWED(2, "用户关注", NotificationCategory.SOCIAL, "social.follow"),
    COMMENT_REPLIED(3, "评论回复", NotificationCategory.INTERACTION, "interaction.reply"),
    SYSTEM_ANNOUNCEMENT(4, "系统公告", NotificationCategory.SYSTEM, "system.notice"),
    POST_PUBLISHED_BY_FOLLOWING(5, "关注作者发文", NotificationCategory.CONTENT, "content.post-published"),
    POST_PUBLISHED_DIGEST(6, "关注作者发文摘要", NotificationCategory.CONTENT, "content.post-published-digest"),
    SECURITY_ALERT(7, "安全提醒", NotificationCategory.SECURITY, "security.alert"),

    /**
     * 旧通知类型别名，保留给历史数据和兼容逻辑。
     */
    @Deprecated
    LIKE(0, "点赞", NotificationCategory.INTERACTION, "interaction.like"),
    @Deprecated
    COMMENT(1, "评论", NotificationCategory.INTERACTION, "interaction.comment"),
    @Deprecated
    FOLLOW(2, "关注", NotificationCategory.SOCIAL, "social.follow"),
    @Deprecated
    REPLY(3, "回复", NotificationCategory.INTERACTION, "interaction.reply"),
    @Deprecated
    SYSTEM(4, "系统", NotificationCategory.SYSTEM, "system.notice"),
    @Deprecated
    POST_PUBLISHED(5, "发布", NotificationCategory.CONTENT, "content.post-published");

    private final int code;
    private final String description;
    private final NotificationCategory category;
    private final String eventCode;

    NotificationType(int code,
                     String description,
                     NotificationCategory category,
                     String eventCode) {
        this.code = code;
        this.description = description;
        this.category = category;
        this.eventCode = eventCode;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public String getEventCode() {
        return eventCode;
    }

    /**
     * 优先使用字符串通知类型恢复，兼容旧的整型 type 列。
     */
    public static NotificationType fromValue(String notificationType, Integer code) {
        if (notificationType != null && !notificationType.isBlank()) {
            return NotificationType.valueOf(notificationType);
        }
        if (code == null) {
            throw new IllegalArgumentException("Notification type value is required");
        }
        return fromCode(code);
    }

    public static NotificationType fromCode(int code) {
        return switch (code) {
            case 0 -> LIKE;
            case 1 -> COMMENT;
            case 2 -> FOLLOW;
            case 3 -> REPLY;
            case 4 -> SYSTEM;
            case 5 -> POST_PUBLISHED_BY_FOLLOWING;
            case 6 -> POST_PUBLISHED_DIGEST;
            case 7 -> SECURITY_ALERT;
            default -> throw new IllegalArgumentException("Unknown notification type code: " + code);
        };
    }
}
