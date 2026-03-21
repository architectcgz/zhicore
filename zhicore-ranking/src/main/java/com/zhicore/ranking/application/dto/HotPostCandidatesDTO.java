package com.zhicore.ranking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 热门文章候选集 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热门文章候选集")
public class HotPostCandidatesDTO {

    @Schema(description = "候选集版本", example = "20260321193000")
    private String version;

    @Schema(description = "候选集生成时间", example = "2026-03-21T19:30:00Z")
    private Instant generatedAt;

    @Schema(description = "候选集最大容量", example = "200")
    private Integer candidateSize;

    @Schema(description = "候选集是否陈旧", example = "false")
    private boolean stale;

    @Schema(description = "候选项列表")
    private List<HotPostCandidateItemDTO> items;
}
