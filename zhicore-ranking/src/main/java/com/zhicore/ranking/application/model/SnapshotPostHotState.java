package com.zhicore.ranking.application.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 快照刷新所需的文章热度状态模型。
 */
@Value
@Builder
public class SnapshotPostHotState {

    Long postId;
    Long authorId;
    List<Long> topicIds;
    OffsetDateTime publishedAt;
    long viewCount;
    int likeCount;
    int favoriteCount;
    int commentCount;
}
