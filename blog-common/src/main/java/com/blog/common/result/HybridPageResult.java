package com.blog.common.result;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 混合分页结果
 * 
 * 支持两种分页模式的响应：
 * - Offset 模式：返回 page, total, pages 等信息
 * - Cursor 模式：返回 nextCursor, hasMore 等信息
 *
 * @author Blog Team
 */
@Data
@Builder
public class HybridPageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 当前页码（Offset 模式）
     */
    private Integer page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总记录数（Offset 模式）
     */
    private Long total;

    /**
     * 总页数（Offset 模式）
     */
    private Integer pages;

    /**
     * 下一页游标（Cursor 模式）
     */
    private String nextCursor;

    /**
     * 是否有更多数据
     */
    private boolean hasMore;

    /**
     * 分页模式：offset 或 cursor
     */
    private String paginationMode;

    /**
     * 是否建议切换到 Cursor 模式
     * 当页码超过阈值时为 true
     */
    private Boolean suggestCursorMode;

    /**
     * 创建 Offset 分页结果
     */
    public static <T> HybridPageResult<T> ofOffset(List<T> items, int page, int size, long total) {
        int pages = (int) Math.ceil((double) total / size);
        return HybridPageResult.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .pages(pages)
                .hasMore(page < pages)
                .paginationMode("offset")
                .suggestCursorMode(false)
                .build();
    }

    /**
     * 创建 Offset 分页结果（建议切换到 Cursor 模式）
     */
    public static <T> HybridPageResult<T> ofOffsetWithSuggestion(List<T> items, int page, int size, long total) {
        int pages = (int) Math.ceil((double) total / size);
        return HybridPageResult.<T>builder()
                .items(items)
                .page(page)
                .size(size)
                .total(total)
                .pages(pages)
                .hasMore(page < pages)
                .paginationMode("offset")
                .suggestCursorMode(true)
                .build();
    }

    /**
     * 创建 Cursor 分页结果
     */
    public static <T> HybridPageResult<T> ofCursor(List<T> items, String nextCursor, boolean hasMore, int size) {
        return HybridPageResult.<T>builder()
                .items(items)
                .size(size)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .paginationMode("cursor")
                .suggestCursorMode(false)
                .build();
    }
}
