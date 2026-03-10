package com.zhicore.ranking.application.model;

import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * inbox 聚合阶段使用的事件模型。
 */
@Data
@Builder
public class AggregationInboxEvent {

    private String eventId;
    private Long postId;
    private Long authorId;
    private RankingMetricType metricType;
    private int countDelta;
    private LocalDateTime occurredAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;
}
