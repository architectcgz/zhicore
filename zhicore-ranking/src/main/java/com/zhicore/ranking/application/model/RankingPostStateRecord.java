package com.zhicore.ranking.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ranking 权威文章状态记录。
 */
@Value
@Builder(toBuilder = true)
public class RankingPostStateRecord {

    Long postId;
    Long authorId;
    OffsetDateTime publishedAt;
    List<Long> topicIds;
    long viewCount;
    int likeCount;
    int favoriteCount;
    int commentCount;
    double rawScore;
    double hotScore;
    long version;
    OffsetDateTime lastBucketStart;
    OffsetDateTime updatedAt;
}
