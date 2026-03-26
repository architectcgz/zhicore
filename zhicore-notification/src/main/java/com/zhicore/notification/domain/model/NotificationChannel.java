package com.zhicore.notification.domain.model;

import lombok.Getter;

/**
 * 通知触达渠道
 */
@Getter
public enum NotificationChannel {

    IN_APP(0, "站内"),
    WEBSOCKET(1, "实时推送"),
    EMAIL(2, "邮件"),
    SMS(3, "短信");

    private final int code;
    private final String description;

    NotificationChannel(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static NotificationChannel fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("通知渠道不能为空");
        }
        try {
            return NotificationChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法通知渠道: " + value);
        }
    }
}
