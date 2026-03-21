package com.zhicore.ranking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热门文章候选项 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热门文章候选项")
public class HotPostCandidateItemDTO {

    @Schema(description = "文章 ID", example = "189000000000000101")
    private String postId;

    @Schema(description = "候选排名（从 1 开始）", example = "1")
    private Integer rank;

    @Schema(description = "热度分数", example = "1250.4")
    private Double score;
}
