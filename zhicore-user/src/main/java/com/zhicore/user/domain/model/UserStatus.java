package com.zhicore.user.domain.model;

import lombok.Getter;

/**
 * 用户状态枚举
 *
 * @author ZhiCore Team
 */
@Getter
public enum UserStatus {

    /**
     * 活跃状态
     */
    ACTIVE(0, "活跃"),

    /**
     * 禁用状态
     */
    DISABLED(1, "禁用"),

    /**
     * 待验证状态
     */
    PENDING_VERIFICATION(2, "待验证");

    private final int code;
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown user status code: " + code);
    }
}
