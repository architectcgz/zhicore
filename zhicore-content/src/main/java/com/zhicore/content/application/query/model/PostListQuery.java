package com.zhicore.content.application.query.model;

import com.zhicore.common.exception.ValidationException;
import lombok.Builder;
import lombok.Value;

/**
 * 文章列表查询模型（R7/R8）
 *
 * 约束：
 * - sort=LATEST 使用光标分页（cursor），禁止 page；
 * - sort=POPULAR 使用偏移分页（page），禁止 cursor；
 * - size 取值 1~100；
 * - page 为 1-based。
 */
@Value
@Builder(toBuilder = true)
public class PostListQuery {

    Integer page;
    String cursor;
    Integer size;
    PostListSort sort;
    String status;

    public int normalizedSize() {
        int s = size == null ? 20 : size;
        if (s < 1 || s > 100) {
            throw new ValidationException("size", "size 必须在 1~100 之间");
        }
        return s;
    }

    public PostListSort normalizedSort() {
        if (sort == null) {
            return PostListSort.LATEST;
        }
        return sort;
    }

    public int normalizedPage() {
        int p = page == null ? 1 : page;
        if (p < 1) {
            throw new ValidationException("page", "page 必须为正整数（从 1 开始）");
        }
        return p;
    }

    public void validate() {
        normalizedSize();

        PostListSort s = normalizedSort();
        if (s == PostListSort.LATEST && page != null) {
            throw new ValidationException("page", "LATEST 排序不支持 page 参数，请使用 cursor 分页");
        }
        if (s == PostListSort.POPULAR && cursor != null && !cursor.isBlank()) {
            throw new ValidationException("cursor", "POPULAR 排序不支持 cursor 参数，请使用 page 分页");
        }

        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!"PUBLISHED".equals(normalized) && !"ALL".equals(normalized)) {
                throw new ValidationException("status", "status 仅支持：PUBLISHED");
            }
        }
    }
}
