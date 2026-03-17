package com.zhicore.ranking.application.model;

import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Ranking 事实事件账本记录。
 */
@Value
@Builder
public class RankingLedgerEventRecord {

    String eventId;
    String eventType;
    Long postId;
    Long actorId;
    Long authorId;
    RankingMetricType metricType;
    int delta;
    LocalDateTime occurredAt;
    LocalDateTime publishedAt;
    String partitionKey;
    String sourceService;
    String sourceOpId;
    LocalDateTime createdAt;
}
