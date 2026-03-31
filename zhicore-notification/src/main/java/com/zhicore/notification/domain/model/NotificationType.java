package com.zhicore.notification.domain.model;

import lombok.Getter;

/**
 * 通知类型枚举
 *
 * @author ZhiCore Team
 */
@Getter
public enum NotificationType {

    /**
     * 文章点赞通知
     */
    POST_LIKED(0, "文章点赞"),

    /**
     * 文章评论通知
     */
    POST_COMMENTED(1, "文章评论"),

    /**
     * 用户关注通知
     */
    USER_FOLLOWED(2, "用户关注"),

    /**
     * 评论回复通知
     */
    COMMENT_REPLIED(3, "评论回复"),

    /**
     * 关注作者发文通知
     */
    POST_PUBLISHED_BY_FOLLOWING(5, "关注作者发文"),

    /**
     * 关注作者发文摘要通知
     */
    POST_PUBLISHED_DIGEST(6, "关注作者发文摘要"),

    /**
     * 系统公告通知
     */
    SYSTEM_ANNOUNCEMENT(4, "系统公告"),

    /**
     * 安全提醒通知
     */
    SECURITY_ALERT(7, "安全提醒"),

    /**
     * 点赞通知
     */
    @Deprecated
    LIKE(0, "点赞"),

    /**
     * 评论通知
     */
    @Deprecated
    COMMENT(1, "评论"),

    /**
     * 关注通知
     */
    @Deprecated
    FOLLOW(2, "关注"),

    /**
     * 回复通知
     */
    @Deprecated
    REPLY(3, "回复"),

    /**
     * 系统通知
     */
    @Deprecated
    SYSTEM(4, "系统");

    private final int code;
    private final String description;

    NotificationType(int code, String description) {
        this.code = code;
        this.description = description;
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

    /**
     * 根据code获取枚举
     */
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
