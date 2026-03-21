package com.zhicore.ranking.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 热门文章候选集元数据。
 */
@Value
@Builder(toBuilder = true)
public class HotPostCandidateMeta {

    String version;
    Instant generatedAt;
    int candidateSize;
    String sourceKey;
    long sourceCount;
    double minScore;
    boolean stale;
}
