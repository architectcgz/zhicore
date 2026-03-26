package com.zhicore.notification.domain.model;

import lombok.Getter;

/**
 * 作者订阅级别
 */
@Getter
public enum AuthorSubscriptionLevel {

    ALL("接收即时通知"),
    DIGEST_ONLY("仅接收摘要"),
    MUTED("静默");

    private final String description;

    AuthorSubscriptionLevel(String description) {
        this.description = description;
    }

    public static AuthorSubscriptionLevel fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("订阅级别不能为空");
        }
        try {
            return AuthorSubscriptionLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法订阅级别: " + value);
        }
    }
}
