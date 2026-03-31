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

    public static NotificationCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("通知分类不能为空");
        }
        try {
            return NotificationCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法通知分类: " + value);
        }
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
