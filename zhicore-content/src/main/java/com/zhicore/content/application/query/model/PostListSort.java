package com.zhicore.content.application.query.model;

import com.zhicore.common.exception.ValidationException;

/**
 * 列表排序类型
 */
public enum PostListSort {
    LATEST,
    POPULAR;

    public static PostListSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }
        try {
            return PostListSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("sort", "sort 仅支持：LATEST、POPULAR");
        }
    }
}

