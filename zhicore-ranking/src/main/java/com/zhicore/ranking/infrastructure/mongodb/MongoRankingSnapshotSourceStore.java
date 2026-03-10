package com.zhicore.ranking.infrastructure.mongodb;

import com.zhicore.ranking.application.model.SnapshotInboxEvent;
import com.zhicore.ranking.application.model.SnapshotPostHotState;
import com.zhicore.ranking.application.port.store.RankingSnapshotSourceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 MongoDB 的排行快照数据源实现。
 */
@Component
@RequiredArgsConstructor
public class MongoRankingSnapshotSourceStore implements RankingSnapshotSourceStore {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final RankingPostHotStateRepository postHotStateRepository;
    private final RankingEventInboxRepository inboxRepository;

    @Override
    public List<SnapshotPostHotState> listActivePostStates() {
        return postHotStateRepository.findByStatus(ACTIVE_STATUS).stream()
                .map(state -> SnapshotPostHotState.builder()
                        .postId(state.getPostId())
                        .authorId(state.getAuthorId())
                        .topicIds(state.getTopicIds())
                        .publishedAt(state.getPublishedAt())
                        .viewCount(state.getViewCount())
                        .likeCount(state.getLikeCount())
                        .favoriteCount(state.getFavoriteCount())
                        .commentCount(state.getCommentCount())
                        .build())
                .toList();
    }

    @Override
    public List<SnapshotInboxEvent> listDoneEventsBetween(LocalDateTime start, LocalDateTime end) {
        return inboxRepository.findByStatusAndOccurredAtBetweenOrderByOccurredAtAsc(
                        RankingEventInbox.InboxStatus.DONE,
                        start,
                        end
                ).stream()
                .map(event -> SnapshotInboxEvent.builder()
                        .postId(event.getPostId())
                        .metricType(event.getMetricType())
                        .countDelta(event.getCountDelta())
                        .build())
                .toList();
    }
}
