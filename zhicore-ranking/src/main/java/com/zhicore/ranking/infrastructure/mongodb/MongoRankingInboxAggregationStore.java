package com.zhicore.ranking.infrastructure.mongodb;

import com.zhicore.ranking.application.model.AggregationInboxEvent;
import com.zhicore.ranking.application.model.AggregationPostHotState;
import com.zhicore.ranking.application.port.store.RankingInboxAggregationStore;
import com.zhicore.ranking.infrastructure.config.RankingInboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 MongoDB 的 ranking inbox 聚合存储实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoRankingInboxAggregationStore implements RankingInboxAggregationStore {

    private final MongoTemplate mongoTemplate;
    private final RankingEventInboxRepository inboxRepository;
    private final RankingPostHotStateRepository postHotStateRepository;
    private final RankingInboxProperties inboxProperties;

    @Override
    public List<AggregationInboxEvent> claimPendingEvents() {
        List<AggregationInboxEvent> result = new ArrayList<>();
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
            result.add(toAggregationEvent(claimed));
        }
        return result;
    }

    @Override
    public Map<Long, AggregationPostHotState> findStatesByPostIds(Collection<Long> postIds) {
        Map<Long, AggregationPostHotState> states = new HashMap<>();
        postHotStateRepository.findAllById(postIds).forEach(state -> states.put(
                state.getPostId(),
                AggregationPostHotState.builder()
                        .postId(state.getPostId())
                        .authorId(state.getAuthorId())
                        .topicIds(state.getTopicIds())
                        .publishedAt(state.getPublishedAt())
                        .status(state.getStatus())
                        .viewCount(state.getViewCount())
                        .likeCount(state.getLikeCount())
                        .favoriteCount(state.getFavoriteCount())
                        .commentCount(state.getCommentCount())
                        .rawScoreCache(state.getRawScoreCache())
                        .lastEventAt(state.getLastEventAt())
                        .updatedAt(state.getUpdatedAt())
                        .recentAppliedEventIds(state.getRecentAppliedEventIds())
                        .build()
        ));
        return states;
    }

    @Override
    public void saveState(AggregationPostHotState state) {
        postHotStateRepository.save(RankingPostHotState.builder()
                .postId(state.getPostId())
                .authorId(state.getAuthorId())
                .topicIds(state.getTopicIds())
                .publishedAt(state.getPublishedAt())
                .status(state.getStatus())
                .viewCount(state.getViewCount())
                .likeCount(state.getLikeCount())
                .favoriteCount(state.getFavoriteCount())
                .commentCount(state.getCommentCount())
                .rawScoreCache(state.getRawScoreCache())
                .lastEventAt(state.getLastEventAt())
                .updatedAt(state.getUpdatedAt())
                .recentAppliedEventIds(state.getRecentAppliedEventIds())
                .build());
    }

    @Override
    public void markDone(Collection<AggregationInboxEvent> events) {
        LocalDateTime now = LocalDateTime.now();
        for (AggregationInboxEvent event : events) {
            Query query = Query.query(Criteria.where("_id").is(event.getEventId()));
            Update update = new Update()
                    .set("status", RankingEventInbox.InboxStatus.DONE)
                    .unset("leaseUntil")
                    .unset("lastError")
                    .set("updatedAt", now);
            mongoTemplate.updateFirst(query, update, RankingEventInbox.class);
        }
    }

    @Override
    public void markFailed(Collection<AggregationInboxEvent> events, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        for (AggregationInboxEvent event : events) {
            int nextRetry = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
            RankingEventInbox.InboxStatus nextStatus = nextRetry >= inboxProperties.getMaxRetry()
                    ? RankingEventInbox.InboxStatus.FAILED
                    : RankingEventInbox.InboxStatus.NEW;

            Query query = Query.query(Criteria.where("_id").is(event.getEventId()));
            Update update = new Update()
                    .set("status", nextStatus)
                    .set("retryCount", nextRetry)
                    .set("lastError", errorMessage)
                    .unset("leaseUntil")
                    .set("updatedAt", now);
            mongoTemplate.updateFirst(query, update, RankingEventInbox.class);
        }
    }

    @Override
    public void cleanupExpiredDoneEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(inboxProperties.getDoneRetentionDays());
        long deleted = inboxRepository.deleteByStatusAndOccurredAtBefore(RankingEventInbox.InboxStatus.DONE, cutoff);
        if (deleted > 0) {
            log.info("清理过期 ranking inbox DONE 记录: {}", deleted);
        }
    }

    @Override
    public int recentAppliedEventWindowSize() {
        return inboxProperties.getAppliedEventWindowSize();
    }

    private AggregationInboxEvent toAggregationEvent(RankingEventInbox event) {
        return AggregationInboxEvent.builder()
                .eventId(event.getEventId())
                .postId(event.getPostId())
                .authorId(event.getAuthorId())
                .metricType(event.getMetricType())
                .countDelta(event.getCountDelta())
                .occurredAt(event.getOccurredAt())
                .publishedAt(event.getPublishedAt())
                .retryCount(event.getRetryCount())
                .build();
    }
}
