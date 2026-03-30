package com.zhicore.comment.application.dto;

import java.time.OffsetDateTime;

/**
 * ranking 热门文章候选集同步元信息。
 */
public record RankingHotPostCandidateMetadata(
        OffsetDateTime syncedAt,
        int candidateCount
) {
}
