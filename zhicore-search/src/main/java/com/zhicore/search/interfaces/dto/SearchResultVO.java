package com.zhicore.search.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索结果 VO
 *
 * @author ZhiCore Team
 */
@Schema(description = "搜索结果分页对象")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultVO<T> {

    /**
     * 搜索结果列表
     */
    @Schema(description = "搜索结果列表")
    private List<T> items;

    /**
     * 总记录数
     */
    @Schema(description = "总记录数", example = "100")
    private long total;

    /**
     * 当前页码
     */
    @Schema(description = "当前页码（从0开始）", example = "0")
    private int page;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "10")
    private int size;

    /**
     * 总页数
     */
    @Schema(description = "总页数", example = "10")
    private int totalPages;

    /**
     * 是否有下一页
     */
    @Schema(description = "是否有下一页", example = "true")
    private boolean hasNext;

    /**
     * 是否有上一页
     */
    @Schema(description = "是否有上一页", example = "false")
    private boolean hasPrevious;

    public static <T> SearchResultVO<T> of(List<T> items, long total, int page, int size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return SearchResultVO.<T>builder()
            .items(items)
            .total(total)
            .page(page)
            .size(size)
            .totalPages(totalPages)
            .hasNext(page < totalPages - 1)
            .hasPrevious(page > 0)
            .build();
    }
}
