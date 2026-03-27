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
     * 点赞通知
     */
    LIKE(0, "点赞", NotificationCategory.INTERACTION, "interaction.like"),

    /**
     * 评论通知
     */
    COMMENT(1, "评论", NotificationCategory.INTERACTION, "interaction.comment"),

    /**
     * 关注通知
     */
    FOLLOW(2, "关注", NotificationCategory.INTERACTION, "interaction.follow"),

    /**
     * 回复通知
     */
    REPLY(3, "回复", NotificationCategory.INTERACTION, "interaction.reply"),

    /**
     * 系统通知
     */
    SYSTEM(4, "系统", NotificationCategory.SYSTEM, "system.notice");

    private final int code;
    private final String description;
    private final NotificationCategory category;
    private final String eventCode;

    NotificationType(int code, String description, NotificationCategory category, String eventCode) {
        this.code = code;
        this.description = description;
        this.category = category;
        this.eventCode = eventCode;
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
