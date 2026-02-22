package com.zhicore.common.result;

import lombok.Data;

/**
 * 混合分页请求
 * 
 * 支持两种分页模式：
 * - Offset 分页：页码 ≤ 阈值时使用，支持跳页
 * - Cursor 分页：页码 > 阈值时使用，性能更好
 *
 * @author ZhiCore Team
 */
@Data
public class HybridPageRequest {

    /**
     * 页码（从 1 开始，用于 Offset 分页）
     */
    private Integer page;

    /**
     * 游标（用于 Cursor 分页）
     */
    private String cursor;

    /**
     * 每页大小
     */
    private int size = 20;

    /**
     * 排序字段
     */
    private String sortBy;

    /**
     * 排序方向 (ASC/DESC)
     */
    private String sortOrder = "DESC";

    /**
     * 混合分页阈值（默认为 5）
     * 页码 ≤ 阈值使用 Offset 分页
     * 页码 > 阈值使用 Cursor 分页
     */
    private int hybridThreshold = 5;

    /**
     * 是否为 Offset 分页模式
     */
    public boolean isOffsetMode() {
        return page != null && cursor == null;
    }

    /**
     * 是否为 Cursor 分页模式
     */
    public boolean isCursorMode() {
        return cursor != null;
    }

    /**
     * 是否超过混合分页阈值
     */
    public boolean exceedsHybridThreshold() {
        return page != null && page > hybridThreshold;
    }

    /**
     * 获取 Offset（用于数据库查询）
     */
    public int getOffset() {
        if (page == null || page < 1) {
            return 0;
        }
        return (page - 1) * size;
    }

    /**
     * 创建 Offset 分页请求
     */
    public static HybridPageRequest ofOffset(int page, int size) {
        HybridPageRequest request = new HybridPageRequest();
        request.setPage(page);
        request.setSize(size);
        return request;
    }

    /**
     * 创建 Cursor 分页请求
     */
    public static HybridPageRequest ofCursor(String cursor, int size) {
        HybridPageRequest request = new HybridPageRequest();
        request.setCursor(cursor);
        request.setSize(size);
        return request;
    }
}
