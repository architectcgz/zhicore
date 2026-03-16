package com.zhicore.ranking.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Ranking 窗口聚合记录。
 */
@Value
@Builder(toBuilder = true)
public class RankingBucketRecord {

    LocalDateTime bucketStart;
    Long postId;
    long viewDelta;
    int likeDelta;
    int favoriteDelta;
    int commentDelta;
    long appliedViewDelta;
    int appliedLikeDelta;
    int appliedFavoriteDelta;
    int appliedCommentDelta;
    String flushOwner;
    LocalDateTime flushStartedAt;
    LocalDateTime flushedAt;
    LocalDateTime updatedAt;

    public long pendingViewDelta() {
        return viewDelta - appliedViewDelta;
    }

    public int pendingLikeDelta() {
        return likeDelta - appliedLikeDelta;
    }

    public int pendingFavoriteDelta() {
        return favoriteDelta - appliedFavoriteDelta;
    }

    public int pendingCommentDelta() {
        return commentDelta - appliedCommentDelta;
    }

    public boolean hasPendingDelta() {
        return pendingViewDelta() != 0
                || pendingLikeDelta() != 0
                || pendingFavoriteDelta() != 0
                || pendingCommentDelta() != 0;
    }

    public RankingBucketRecord toPendingBucket() {
        return toBuilder()
                .viewDelta(pendingViewDelta())
                .likeDelta(pendingLikeDelta())
                .favoriteDelta(pendingFavoriteDelta())
                .commentDelta(pendingCommentDelta())
                .appliedViewDelta(0L)
                .appliedLikeDelta(0)
                .appliedFavoriteDelta(0)
                .appliedCommentDelta(0)
                .build();
    }
}
