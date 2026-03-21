package com.zhicore.comment.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * ranking 热门文章候选集响应。
 */
public record RankingHotPostCandidatesResponse(
        String version,
        Instant generatedAt,
        Integer candidateSize,
        boolean stale,
        List<RankingHotPostCandidateItem> items
) {
}
