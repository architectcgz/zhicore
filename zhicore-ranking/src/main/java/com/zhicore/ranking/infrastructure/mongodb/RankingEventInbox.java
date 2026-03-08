package com.zhicore.ranking.infrastructure.mongodb;

import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Ranking 事件 inbox。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ranking_event_inbox")
@CompoundIndexes({
        @CompoundIndex(name = "idx_status_lease_occurred", def = "{'status': 1, 'leaseUntil': 1, 'occurredAt': 1}"),
        @CompoundIndex(name = "idx_status_occurred", def = "{'status': 1, 'occurredAt': 1}"),
        @CompoundIndex(name = "idx_post_occurred", def = "{'postId': 1, 'occurredAt': -1}")
})
public class RankingEventInbox {

    @Id
    private String eventId;

    private String eventType;

    private Long postId;

    private Long userId;

    private Long authorId;

    private RankingMetricType metricType;

    private int countDelta;

    private double scoreDelta;

    private LocalDateTime occurredAt;

    private LocalDateTime publishedAt;

    private InboxStatus status;

    private LocalDateTime leaseUntil;

    private Integer retryCount;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum InboxStatus {
        NEW,
        PROCESSING,
        DONE,
        FAILED
    }
}
