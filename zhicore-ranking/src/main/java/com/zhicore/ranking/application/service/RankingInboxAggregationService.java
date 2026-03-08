package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingInboxProperties;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInbox;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInboxRepository;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotState;
import com.zhicore.ranking.infrastructure.mongodb.RankingPostHotStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

    private final MongoTemplate mongoTemplate;
    private final RankingEventInboxRepository inboxRepository;
    private final RankingPostHotStateRepository postHotStateRepository;
    private final RankingInboxProperties inboxProperties;
    private final PostMetadataResolver postMetadataResolver;
    private final HotScoreCalculator hotScoreCalculator;

    public int aggregatePendingEvents() {
        List<RankingEventInbox> claimedEvents = claimBatch();
        if (claimedEvents.isEmpty()) {
            cleanupExpiredDoneEvents();
            return 0;
        }

        Map<Long, List<RankingEventInbox>> grouped = claimedEvents.stream()
                .filter(event -> event.getPostId() != null)
                .sorted(Comparator.comparing(RankingEventInbox::getOccurredAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .collect(java.util.stream.Collectors.groupingBy(
                        RankingEventInbox::getPostId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        Map<Long, RankingPostHotState> existingStates = new HashMap<>();
        postHotStateRepository.findAllById(grouped.keySet())
                .forEach(state -> existingStates.put(state.getPostId(), state));
        Map<Long, PostMetadataResolver.PostMetadata> metadataMap = postMetadataResolver.resolve(grouped.keySet());

        int processed = 0;
        for (Map.Entry<Long, List<RankingEventInbox>> entry : grouped.entrySet()) {
            List<RankingEventInbox> events = entry.getValue();
            try {
                RankingPostHotState state = applyEvents(
                        existingStates.get(entry.getKey()),
                        metadataMap.get(entry.getKey()),
                        events
                );
                postHotStateRepository.save(state);
                markDone(events);
                processed += events.size();
            } catch (Exception e) {
                log.error("聚合 ranking inbox 事件失败: postId={}, eventCount={}", entry.getKey(), events.size(), e);
                markFailed(events, e);
            }
        }

        cleanupExpiredDoneEvents();
        return processed;
    }

    private List<RankingEventInbox> claimBatch() {
        List<RankingEventInbox> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusSeconds(inboxProperties.getLeaseSeconds());

        for (int i = 0; i < inboxProperties.getBatchSize(); i++) {
            Query query = new Query(new Criteria().orOperator(
                    Criteria.where("status").is(RankingEventInbox.InboxStatus.NEW),
                    new Criteria().andOperator(
                            Criteria.where("status").is(RankingEventInbox.InboxStatus.PROCESSING),
                            Criteria.where("leaseUntil").lt(now)
                    )
            ));
            query.with(Sort.by(Sort.Direction.ASC, "occurredAt"));

            Update update = new Update()
                    .set("status", RankingEventInbox.InboxStatus.PROCESSING)
                    .set("leaseUntil", leaseUntil)
                    .set("updatedAt", now)
                    .unset("lastError");

            RankingEventInbox claimed = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    RankingEventInbox.class
            );
            if (claimed == null) {
                break;
            }
            result.add(claimed);
        }
        return result;
    }

    private RankingPostHotState applyEvents(RankingPostHotState existingState,
                                            PostMetadataResolver.PostMetadata metadata,
                                            List<RankingEventInbox> events) {
        RankingEventInbox seed = events.get(0);
        RankingPostHotState state = existingState != null ? existingState : RankingPostHotState.builder()
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
        for (RankingEventInbox event : events) {
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

    private void mergeMetadata(RankingPostHotState state,
                               PostMetadataResolver.PostMetadata metadata,
                               RankingEventInbox seed) {
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

    private void applyCountDelta(RankingPostHotState state, RankingMetricType metricType, int delta) {
        switch (metricType) {
            case VIEW -> state.setViewCount(Math.max(0L, state.getViewCount() + delta));
            case LIKE -> state.setLikeCount(Math.max(0, state.getLikeCount() + delta));
            case FAVORITE -> state.setFavoriteCount(Math.max(0, state.getFavoriteCount() + delta));
            case COMMENT -> state.setCommentCount(Math.max(0, state.getCommentCount() + delta));
        }
    }

    private double calculateRawScoreCache(RankingPostHotState state) {
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

    private void markDone(Collection<RankingEventInbox> events) {
        LocalDateTime now = LocalDateTime.now();
        for (RankingEventInbox event : events) {
            Query query = Query.query(Criteria.where("_id").is(event.getEventId()));
            Update update = new Update()
                    .set("status", RankingEventInbox.InboxStatus.DONE)
                    .unset("leaseUntil")
                    .unset("lastError")
                    .set("updatedAt", now);
            mongoTemplate.updateFirst(query, update, RankingEventInbox.class);
        }
    }

    private void markFailed(Collection<RankingEventInbox> events, Exception error) {
        LocalDateTime now = LocalDateTime.now();
        for (RankingEventInbox event : events) {
            int nextRetry = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
            RankingEventInbox.InboxStatus nextStatus = nextRetry >= inboxProperties.getMaxRetry()
                    ? RankingEventInbox.InboxStatus.FAILED
                    : RankingEventInbox.InboxStatus.NEW;

            Query query = Query.query(Criteria.where("_id").is(event.getEventId()));
            Update update = new Update()
                    .set("status", nextStatus)
                    .set("retryCount", nextRetry)
                    .set("lastError", error.getMessage())
                    .unset("leaseUntil")
                    .set("updatedAt", now);
            mongoTemplate.updateFirst(query, update, RankingEventInbox.class);
        }
    }

    private void cleanupExpiredDoneEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(inboxProperties.getDoneRetentionDays());
        long deleted = inboxRepository.deleteByStatusAndOccurredAtBefore(RankingEventInbox.InboxStatus.DONE, cutoff);
        if (deleted > 0) {
            log.info("清理过期 ranking inbox DONE 记录: {}", deleted);
        }
    }

    private void trimRecentAppliedIds(List<String> recentAppliedEventIds) {
        int limit = inboxProperties.getAppliedEventWindowSize();
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
