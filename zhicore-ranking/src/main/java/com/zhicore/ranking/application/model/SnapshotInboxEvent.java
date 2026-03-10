package com.zhicore.ranking.application.model;

import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.Builder;
import lombok.Value;

/**
 * 快照刷新所需的 inbox 事件模型。
 */
@Value
@Builder
public class SnapshotInboxEvent {

    Long postId;
    RankingMetricType metricType;
    int countDelta;
}
