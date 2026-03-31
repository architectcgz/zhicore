package com.zhicore.notification.domain.model;

/**
 * 通知分类。
 */
public enum NotificationCategory {

    INTERACTION(0, "互动"),
    CONTENT(1, "内容"),
    SYSTEM(2, "系统"),
    SECURITY(3, "安全"),
    SOCIAL(4, "社交");

    private final int code;
    private final String description;

    NotificationCategory(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static NotificationCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("通知分类不能为空");
        }
        try {
            return NotificationCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法通知分类: " + value, ex);
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
