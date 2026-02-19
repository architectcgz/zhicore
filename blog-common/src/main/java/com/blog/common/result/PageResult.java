package com.blog.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private long current;

    /**
     * 每页大小
     */
    private long size;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 游标（用于游标分页）
     */
    private String cursor;

    public PageResult() {
    }

    public PageResult(long current, long size, long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records;
        this.pages = (total + size - 1) / size;
        this.hasNext = current < this.pages;
    }

    /**
     * 创建游标分页结果
     */
    public static <T> PageResult<T> cursor(List<T> records, String cursor, boolean hasNext) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setCursor(cursor);
        result.setHasNext(hasNext);
        result.setSize(records.size());
        return result;
    }

    /**
     * 创建普通分页结果
     */
    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        return new PageResult<>(current, size, total, records);
    }
}
