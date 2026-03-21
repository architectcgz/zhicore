package com.zhicore.comment.application.dto;

/**
 * ranking 热门候选项。
 */
public record RankingHotPostCandidateItem(
        String postId,
        Integer rank,
        Double score
) {
}
