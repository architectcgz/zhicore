package com.zhicore.comment.application.dto;

import java.time.LocalDateTime;

/**
 * ranking 热门文章候选集同步元信息。
 */
public record RankingHotPostCandidateMetadata(
        LocalDateTime syncedAt,
        int candidateCount
) {
}
