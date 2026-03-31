package com.zhicore.notification.domain.model;

/**
 * 通知重要程度
 */
public enum NotificationImportance {

    NORMAL(0, "普通"),
    HIGH(1, "重要"),
    CRITICAL(2, "紧急");

    private final int code;
    private final String description;

    NotificationImportance(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static NotificationImportance fromCode(int code) {
        for (NotificationImportance importance : values()) {
            if (importance.code == code) {
                return importance;
            }
        }
        throw new IllegalArgumentException("Unknown notification importance code: " + code);
    }
}
