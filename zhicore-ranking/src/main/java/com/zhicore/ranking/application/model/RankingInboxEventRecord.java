package com.zhicore.ranking.application.model;

import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Ranking inbox 写入事件模型。
 */
@Value
@Builder
public class RankingInboxEventRecord {

    String eventId;
    String eventType;
    Long postId;
    Long userId;
    Long authorId;
    RankingMetricType metricType;
    int countDelta;
    double scoreDelta;
    LocalDateTime occurredAt;
    LocalDateTime publishedAt;
    int retryCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
