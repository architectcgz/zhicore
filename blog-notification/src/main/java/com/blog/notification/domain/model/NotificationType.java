package com.blog.notification.domain.model;

import lombok.Getter;

/**
 * 通知类型枚举
 *
 * @author Blog Team
 */
@Getter
public enum NotificationType {

    /**
     * 点赞通知
     */
    LIKE(0, "点赞"),

    /**
     * 评论通知
     */
    COMMENT(1, "评论"),

    /**
     * 关注通知
     */
    FOLLOW(2, "关注"),

    /**
     * 回复通知
     */
    REPLY(3, "回复"),

    /**
     * 系统通知
     */
    SYSTEM(4, "系统");

    private final int code;
    private final String description;

    NotificationType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static NotificationType fromCode(int code) {
        for (NotificationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown notification type code: " + code);
    }
}
