package com.zhicore.comment.domain.model;

import lombok.Getter;

/**
 * 评论状态枚举
 *
 * @author ZhiCore Team
 */
@Getter
public enum CommentStatus {

    /**
     * 正常
     */
    NORMAL(0, "正常"),

    /**
     * 已删除
     */
    DELETED(1, "已删除"),

    /**
     * 审核中
     */
    PENDING(2, "审核中"),

    /**
     * 已隐藏
     */
    HIDDEN(3, "已隐藏");

    private final int code;
    private final String description;

    CommentStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取状态
     */
    public static CommentStatus fromCode(int code) {
        for (CommentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown comment status code: " + code);
    }
}
