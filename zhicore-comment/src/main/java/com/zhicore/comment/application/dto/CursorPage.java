package com.zhicore.comment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游标分页响应
 *
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPage<T> {

    /**
     * 数据列表
     */
    private List<T> content;

    /**
     * 下一页游标（null表示没有更多）
     */
    private String nextCursor;

    /**
     * 是否有更多数据
     */
    private boolean hasMore;

    public static <T> CursorPage<T> of(List<T> content, String nextCursor, boolean hasMore) {
        return new CursorPage<>(content, nextCursor, hasMore);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
