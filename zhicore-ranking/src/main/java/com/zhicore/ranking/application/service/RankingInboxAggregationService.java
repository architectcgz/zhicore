package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.AggregationInboxEvent;
import com.zhicore.ranking.application.model.AggregationPostHotState;
import com.zhicore.ranking.application.port.store.RankingInboxAggregationStore;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聚合 ranking inbox 事件并更新文章热度权威状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingInboxAggregationService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final RankingInboxAggregationStore rankingInboxAggregationStore;
    private final PostMetadataResolver postMetadataResolver;
    private final HotScoreCalculator hotScoreCalculator;

    public int aggregatePendingEvents() {
        List<AggregationInboxEvent> claimedEvents = rankingInboxAggregationStore.claimPendingEvents();
        if (claimedEvents.isEmpty()) {
            rankingInboxAggregationStore.cleanupExpiredDoneEvents();
            return 0;
        }

        Map<Long, List<AggregationInboxEvent>> grouped = claimedEvents.stream()
                .filter(event -> event.getPostId() != null)
                .sorted(Comparator.comparing(AggregationInboxEvent::getOccurredAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .collect(java.util.stream.Collectors.groupingBy(
                        AggregationInboxEvent::getPostId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        Map<Long, AggregationPostHotState> existingStates =
                new HashMap<>(rankingInboxAggregationStore.findStatesByPostIds(grouped.keySet()));
        Map<Long, PostMetadataResolver.PostMetadata> metadataMap = postMetadataResolver.resolve(grouped.keySet());

        int processed = 0;
        for (Map.Entry<Long, List<AggregationInboxEvent>> entry : grouped.entrySet()) {
            List<AggregationInboxEvent> events = entry.getValue();
            try {
                AggregationPostHotState state = applyEvents(
                        existingStates.get(entry.getKey()),
                        metadataMap.get(entry.getKey()),
                        events
                );
                rankingInboxAggregationStore.saveState(state);
                rankingInboxAggregationStore.markDone(events);
                processed += events.size();
            } catch (Exception e) {
                log.error("聚合 ranking inbox 事件失败: postId={}, eventCount={}", entry.getKey(), events.size(), e);
                rankingInboxAggregationStore.markFailed(events, e.getMessage());
            }
        }

        rankingInboxAggregationStore.cleanupExpiredDoneEvents();
        return processed;
    }

    private AggregationPostHotState applyEvents(AggregationPostHotState existingState,
                                                PostMetadataResolver.PostMetadata metadata,
                                                List<AggregationInboxEvent> events) {
        AggregationInboxEvent seed = events.get(0);
        AggregationPostHotState state = existingState != null ? existingState : AggregationPostHotState.builder()
                .postId(seed.getPostId())
                .status(ACTIVE_STATUS)
                .build();

        if (state.getTopicIds() == null) {
            state.setTopicIds(new ArrayList<>());
        }
        if (state.getRecentAppliedEventIds() == null) {
            state.setRecentAppliedEventIds(new ArrayList<>());
        }

        mergeMetadata(state, metadata, seed);

        boolean changed = false;
        for (AggregationInboxEvent event : events) {
            if (state.getRecentAppliedEventIds().contains(event.getEventId())) {
                continue;
            }
            applyCountDelta(state, event.getMetricType(), event.getCountDelta());
            state.getRecentAppliedEventIds().add(event.getEventId());
            state.setLastEventAt(maxTime(state.getLastEventAt(), event.getOccurredAt()));
            changed = true;
        }

        if (changed) {
            trimRecentAppliedIds(state.getRecentAppliedEventIds());
        }

        state.setRawScoreCache(calculateRawScoreCache(state));
        state.setUpdatedAt(LocalDateTime.now());
        return state;
    }

    private void mergeMetadata(AggregationPostHotState state,
                               PostMetadataResolver.PostMetadata metadata,
                               AggregationInboxEvent seed) {
        if (state.getAuthorId() == null) {
            Long authorId = metadata != null ? metadata.getAuthorId() : seed.getAuthorId();
            state.setAuthorId(authorId);
        }
        if ((state.getTopicIds() == null || state.getTopicIds().isEmpty()) && metadata != null) {
            state.setTopicIds(new ArrayList<>(metadata.getTopicIds()));
        }
        if (state.getPublishedAt() == null) {
            LocalDateTime publishedAt = metadata != null ? metadata.getPublishedAt() : seed.getPublishedAt();
            if (publishedAt == null) {
                publishedAt = seed.getOccurredAt();
            }
            state.setPublishedAt(publishedAt);
        }
    }

    private void applyCountDelta(AggregationPostHotState state, RankingMetricType metricType, int delta) {
        switch (metricType) {
            case VIEW -> state.setViewCount(Math.max(0L, state.getViewCount() + delta));
            case LIKE -> state.setLikeCount(Math.max(0, state.getLikeCount() + delta));
            case FAVORITE -> state.setFavoriteCount(Math.max(0, state.getFavoriteCount() + delta));
            case COMMENT -> state.setCommentCount(Math.max(0, state.getCommentCount() + delta));
        }
    }

    private double calculateRawScoreCache(AggregationPostHotState state) {
        PostStats stats = PostStats.builder()
                .viewCount(state.getViewCount())
                .likeCount(state.getLikeCount())
                .commentCount(state.getCommentCount())
                .favoriteCount(state.getFavoriteCount())
                .build();
        return stats.getViewCount() * hotScoreCalculator.getViewDelta()
                + stats.getLikeCount() * hotScoreCalculator.getLikeDelta()
                + stats.getCommentCount() * hotScoreCalculator.getCommentDelta()
                + stats.getFavoriteCount() * hotScoreCalculator.getFavoriteDelta();
    }

    private void trimRecentAppliedIds(List<String> recentAppliedEventIds) {
        int limit = rankingInboxAggregationStore.recentAppliedEventWindowSize();
        if (recentAppliedEventIds.size() <= limit) {
            return;
        }
        int removeCount = recentAppliedEventIds.size() - limit;
        recentAppliedEventIds.subList(0, removeCount).clear();
    }

    private LocalDateTime maxTime(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
