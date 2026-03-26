package com.zhicore.notification.domain.model;

import lombok.Getter;

/**
 * 通知分类
 */
@Getter
public enum NotificationCategory {

    SOCIAL(0, "社交"),
    CONTENT(1, "内容"),
    SYSTEM(2, "系统"),
    SECURITY(3, "安全");

    private final int code;
    private final String description;

    NotificationCategory(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static NotificationCategory fromCode(int code) {
        for (NotificationCategory category : values()) {
            if (category.code == code) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown notification category code: " + code);
    }
}
