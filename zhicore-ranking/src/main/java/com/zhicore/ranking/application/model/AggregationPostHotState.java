package com.zhicore.ranking.application.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * inbox 聚合阶段使用的文章热度状态模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregationPostHotState {

    private Long postId;
    private Long authorId;

    @Builder.Default
    private List<Long> topicIds = new ArrayList<>();

    private LocalDateTime publishedAt;

    @Builder.Default
    private String status = "ACTIVE";

    private long viewCount;
    private int likeCount;
    private int favoriteCount;
    private int commentCount;
    private double rawScoreCache;
    private LocalDateTime lastEventAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<String> recentAppliedEventIds = new ArrayList<>();
}
