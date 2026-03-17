package com.zhicore.ranking.application.model;

import lombok.Builder;
import lombok.Value;

/**
 * 周期榜物化所需的文章周期分数。
 */
@Value
@Builder
public class SnapshotPeriodScore {

    Long postId;
    double deltaScore;
}
