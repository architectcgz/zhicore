package com.zhicore.ranking.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 热度分数值对象
 *
 * @author ZhiCore Team
 */
@Schema(description = "热度分数")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotScore {

    /**
     * 实体ID（文章ID、用户ID等）
     */
    @Schema(description = "实体ID（文章ID、用户ID、话题ID等）", example = "1234567890")
    private String entityId;

    /**
     * 热度分数
     */
    @Schema(description = "热度分数", example = "1250.5")
    private double score;

    /**
     * 排名
     */
    @Schema(description = "排名（从1开始）", example = "1")
    private int rank;

    /**
     * 最后更新时间
     */
    @Schema(description = "最后更新时间", example = "2024-01-28T10:30:00")
    private OffsetDateTime updatedAt;

    /**
     * 创建热度分数
     */
    public static HotScore of(String entityId, double score) {
        return HotScore.builder()
                .entityId(entityId)
                .score(score)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * 创建带排名的热度分数
     */
    public static HotScore ofWithRank(String entityId, double score, int rank) {
        return HotScore.builder()
                .entityId(entityId)
                .score(score)
                .rank(rank)
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
