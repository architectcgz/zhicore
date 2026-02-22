package com.zhicore.content.domain.model;

import lombok.Getter;

/**
 * 文章状态枚举
 *
 * @author ZhiCore Team
 */
@Getter
public enum PostStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    SCHEDULED(2, "定时发布"),
    DELETED(3, "已删除");

    private final int code;
    private final String description;

    PostStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PostStatus fromCode(int code) {
        for (PostStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PostStatus code: " + code);
    }
}
