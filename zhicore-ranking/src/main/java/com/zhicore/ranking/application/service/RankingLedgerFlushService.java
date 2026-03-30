package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.RankingBucketRecord;
import com.zhicore.ranking.application.model.RankingPostStateRecord;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingPipelineProperties;
import com.zhicore.ranking.infrastructure.pg.PgRankingLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 将 bucket 增量物化到权威状态和周期分数表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingLedgerFlushService {

    private static final long MIN_STALE_CLAIM_MILLIS = 15000L;

    private final PgRankingLedgerRepository repository;
    private final PostMetadataResolver postMetadataResolver;
    private final HotScoreCalculator hotScoreCalculator;
    private final RankingPipelineProperties pipelineProperties;
    private final RankingRedisMaterializationService rankingRedisMaterializationService;
    private final String flushOwner = "ranking-flush-" + UUID.randomUUID();

    @Transactional
    public int flushPendingBuckets() {
        return flushPendingBuckets(true);
    }

    @Transactional
    public int flushPendingBuckets(boolean materializeRedis) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RankingBucketRecord> buckets = repository.claimFlushableBuckets(
                pipelineProperties.getFlushBatchSize(),
                flushOwner,
                now,
                resolveFlushUpperBound(now),
                now.minusNanos(claimStaleNanos())
        );
        if (buckets.isEmpty()) {
            return 0;
        }

        Set<Long> postIds = buckets.stream().map(RankingBucketRecord::getPostId).collect(java.util.stream.Collectors.toSet());
        Map<Long, RankingPostStateRecord> existingStates = new HashMap<>(repository.findPostStatesByIds(postIds));
        Map<Long, PostMetadataResolver.PostMetadata> metadataMap = resolveMissingMetadata(postIds, existingStates);

        for (RankingBucketRecord bucket : buckets) {
            RankingBucketRecord pendingBucket = bucket.toPendingBucket();
            if (!pendingBucket.hasPendingDelta()) {
                repository.markBucketFlushed(bucket, flushOwner, now);
                continue;
            }
            RankingPostStateRecord previousState = existingStates.get(bucket.getPostId());
            RankingPostStateRecord nextState = applyBucket(
                    previousState,
                    metadataMap.get(bucket.getPostId()),
                    pendingBucket,
                    now
            );
            repository.savePostState(nextState);

            double periodScoreDelta = calculatePeriodScoreDelta(pendingBucket, nextState.getPublishedAt());
            repository.incrementPeriodScore("DAY", dayKey(bucket.getBucketStart()), bucket.getPostId(), periodScoreDelta, now);
            repository.incrementPeriodScore("WEEK", weekKey(bucket.getBucketStart()), bucket.getPostId(), periodScoreDelta, now);
            repository.incrementPeriodScore("MONTH", monthKey(bucket.getBucketStart()), bucket.getPostId(), periodScoreDelta, now);
            repository.markBucketFlushed(bucket, flushOwner, now);
            if (materializeRedis) {
                registerRedisMaterializationAfterCommit(pendingBucket, previousState, nextState, periodScoreDelta);
            }
            existingStates.put(bucket.getPostId(), nextState);
        }
        return buckets.size();
    }

    private void registerRedisMaterializationAfterCommit(RankingBucketRecord bucket,
                                                         RankingPostStateRecord previousState,
                                                         RankingPostStateRecord nextState,
                                                         double periodScoreDelta) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rankingRedisMaterializationService.applyFlush(bucket, previousState, nextState, periodScoreDelta);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rankingRedisMaterializationService.applyFlush(bucket, previousState, nextState, periodScoreDelta);
                } catch (Exception ex) {
                    log.error("ranking Redis 增量物化失败，等待快照兜底修复: postId={}, bucketStart={}",
                            bucket.getPostId(), bucket.getBucketStart(), ex);
                }
            }
        });
    }

    private Map<Long, PostMetadataResolver.PostMetadata> resolveMissingMetadata(Set<Long> postIds,
                                                                                Map<Long, RankingPostStateRecord> existingStates) {
        Set<Long> missingPostIds = postIds.stream()
                .filter(postId -> requiresMetadata(existingStates.get(postId)))
                .collect(java.util.stream.Collectors.toSet());
        if (missingPostIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return postMetadataResolver.resolveBestEffort(missingPostIds);
    }

    private boolean requiresMetadata(RankingPostStateRecord state) {
        return state == null
                || state.getAuthorId() == null
                || state.getPublishedAt() == null
                || state.getTopicIds() == null
                || state.getTopicIds().isEmpty();
    }

    private RankingPostStateRecord applyBucket(RankingPostStateRecord existingState,
                                               PostMetadataResolver.PostMetadata metadata,
                                               RankingBucketRecord bucket,
                                               OffsetDateTime now) {
        List<Long> topicIds = existingState != null && existingState.getTopicIds() != null
                ? new ArrayList<>(existingState.getTopicIds())
                : new ArrayList<>();
        if (topicIds.isEmpty() && metadata != null && metadata.getTopicIds() != null) {
            topicIds.addAll(metadata.getTopicIds());
        }

        Long authorId = existingState != null && existingState.getAuthorId() != null
                ? existingState.getAuthorId()
                : metadata != null ? metadata.getAuthorId() : null;
        OffsetDateTime publishedAt = existingState != null && existingState.getPublishedAt() != null
                ? existingState.getPublishedAt()
                : metadata != null ? metadata.getPublishedAt() : null;

        long viewCount = Math.max(0L, (existingState != null ? existingState.getViewCount() : 0L) + bucket.getViewDelta());
        int likeCount = Math.max(0, (existingState != null ? existingState.getLikeCount() : 0) + bucket.getLikeDelta());
        int favoriteCount = Math.max(0, (existingState != null ? existingState.getFavoriteCount() : 0) + bucket.getFavoriteDelta());
        int commentCount = Math.max(0, (existingState != null ? existingState.getCommentCount() : 0) + bucket.getCommentDelta());

        PostStats stats = PostStats.builder()
                .viewCount(viewCount)
                .likeCount(likeCount)
                .favoriteCount(favoriteCount)
                .commentCount(commentCount)
                .build();
        double rawScore = calculateRawScore(stats);

        return RankingPostStateRecord.builder()
                .postId(bucket.getPostId())
                .authorId(authorId)
                .publishedAt(publishedAt)
                .topicIds(topicIds)
                .viewCount(viewCount)
                .likeCount(likeCount)
                .favoriteCount(favoriteCount)
                .commentCount(commentCount)
                .rawScore(rawScore)
                .hotScore(hotScoreCalculator.calculatePostHotScore(stats, publishedAt))
                .version(existingState != null ? existingState.getVersion() + 1 : 0L)
                .lastBucketStart(bucket.getBucketStart())
                .updatedAt(now)
                .build();
    }

    private double calculateRawScore(PostStats stats) {
        return stats.getViewCount() * hotScoreCalculator.getViewDelta()
                + stats.getLikeCount() * hotScoreCalculator.getLikeDelta()
                + stats.getFavoriteCount() * hotScoreCalculator.getFavoriteDelta()
                + stats.getCommentCount() * hotScoreCalculator.getCommentDelta();
    }

    private double calculatePeriodScoreDelta(RankingBucketRecord bucket, OffsetDateTime publishedAt) {
        double rawScoreDelta = bucket.getViewDelta() * hotScoreCalculator.getViewDelta()
                + bucket.getLikeDelta() * hotScoreCalculator.getLikeDelta()
                + bucket.getFavoriteDelta() * hotScoreCalculator.getFavoriteDelta()
                + bucket.getCommentDelta() * hotScoreCalculator.getCommentDelta();
        return rawScoreDelta * hotScoreCalculator.calculateTimeDecay(publishedAt);
    }

    private String dayKey(OffsetDateTime bucketStart) {
        return bucketStart.toLocalDate().toString();
    }

    private String weekKey(OffsetDateTime bucketStart) {
        LocalDate date = bucketStart.toLocalDate();
        WeekFields weekFields = WeekFields.ISO;
        return "%d-W%02d".formatted(date.get(weekFields.weekBasedYear()), date.get(weekFields.weekOfWeekBasedYear()));
    }

    private String monthKey(OffsetDateTime bucketStart) {
        LocalDate date = bucketStart.toLocalDate();
        return "%d-%02d".formatted(date.getYear(), date.getMonthValue());
    }

    private OffsetDateTime floorBucketStart(OffsetDateTime occurredAt) {
        long bucketWindowSeconds = pipelineProperties.getBucketWindowSeconds();
        long epochSecond = occurredAt.toEpochSecond();
        long floored = (epochSecond / bucketWindowSeconds) * bucketWindowSeconds;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(floored), java.time.ZoneId.systemDefault());
    }

    private OffsetDateTime resolveFlushUpperBound(OffsetDateTime now) {
        return floorBucketStart(now.minusSeconds(pipelineProperties.getEffectiveFlushDelaySeconds()));
    }

    private long claimStaleNanos() {
        long staleMillis = Math.max(pipelineProperties.getFlushInterval() * 3, MIN_STALE_CLAIM_MILLIS);
        return staleMillis * 1_000_000L;
    }
}
