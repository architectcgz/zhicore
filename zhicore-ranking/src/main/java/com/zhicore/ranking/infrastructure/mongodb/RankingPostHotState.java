package com.zhicore.ranking.infrastructure.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章热度权威状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ranking_post_hot_state")
@CompoundIndexes({
        @CompoundIndex(name = "idx_status_updated", def = "{'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_author", def = "{'authorId': 1}"),
        @CompoundIndex(name = "idx_topic_ids", def = "{'topicIds': 1}")
})
public class RankingPostHotState {

    @Id
    private Long postId;

    private Long authorId;

    @Builder.Default
    private List<Long> topicIds = new ArrayList<>();

    private LocalDateTime publishedAt;

    @Builder.Default
    private String status = "ACTIVE";

    private long viewCount;

    private int likeCount;

    private int favoriteCount;

    private int commentCount;

    private double rawScoreCache;

    private LocalDateTime lastEventAt;

    private LocalDateTime updatedAt;

    @Builder.Default
    private List<String> recentAppliedEventIds = new ArrayList<>();
}
