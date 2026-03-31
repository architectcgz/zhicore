package com.zhicore.ranking.infrastructure.pg;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.ranking.application.model.RankingBucketRecord;
import com.zhicore.ranking.application.model.RankingLedgerEventRecord;
import com.zhicore.ranking.application.model.RankingPostStateRecord;
import com.zhicore.ranking.application.model.SnapshotPeriodScore;
import com.zhicore.ranking.application.model.SnapshotPostHotState;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranking ledger/bucket/post_state PostgreSQL 仓储。
 */
@Repository
@RequiredArgsConstructor
public class PgRankingLedgerRepository {

    /**
     * replay 与 live ingestion 的 PostgreSQL 事务级 advisory lock。
     *
     * <p>ingestion 获取 shared lock，replay 在持有 Redis 互斥锁后获取 exclusive lock，
     * 以等待所有已开始的 ingestion 事务排空，再开始 reset + replay。</p>
     */
    private static final long REPLAY_BARRIER_LOCK_ID = 4_260_001L;

    private static final RowMapper<RankingLedgerEventRecord> LEDGER_EVENT_ROW_MAPPER = (rs, rowNum) ->
            RankingLedgerEventRecord.builder()
                    .eventId(rs.getString("event_id"))
                    .eventType(rs.getString("event_type"))
                    .postId(rs.getLong("post_id"))
                    .actorId(getLongOrNull(rs.getObject("actor_id")))
                    .authorId(getLongOrNull(rs.getObject("author_id")))
                    .metricType(RankingMetricType.valueOf(rs.getString("metric_type")))
                    .delta(rs.getInt("delta"))
                    .occurredAt(DateTimeUtils.toOffsetDateTime(rs.getTimestamp("occurred_at")))
                    .publishedAt(toOffsetDateTime(rs.getTimestamp("published_at")))
                    .partitionKey(rs.getString("partition_key"))
                    .sourceService(rs.getString("source_service"))
                    .sourceOpId(rs.getString("source_op_id"))
                    .createdAt(DateTimeUtils.toOffsetDateTime(rs.getTimestamp("created_at")))
                    .build();

    private static final RowMapper<RankingBucketRecord> BUCKET_ROW_MAPPER = (rs, rowNum) ->
            RankingBucketRecord.builder()
                    .bucketStart(DateTimeUtils.toOffsetDateTime(rs.getTimestamp("bucket_start")))
                    .postId(rs.getLong("post_id"))
                    .viewDelta(rs.getLong("view_delta"))
                    .likeDelta(rs.getInt("like_delta"))
                    .favoriteDelta(rs.getInt("favorite_delta"))
                    .commentDelta(rs.getInt("comment_delta"))
                    .appliedViewDelta(rs.getLong("applied_view_delta"))
                    .appliedLikeDelta(rs.getInt("applied_like_delta"))
                    .appliedFavoriteDelta(rs.getInt("applied_favorite_delta"))
                    .appliedCommentDelta(rs.getInt("applied_comment_delta"))
                    .flushOwner(rs.getString("flush_owner"))
                    .flushStartedAt(toOffsetDateTime(rs.getTimestamp("flush_started_at")))
                    .flushedAt(toOffsetDateTime(rs.getTimestamp("flushed_at")))
                    .updatedAt(DateTimeUtils.toOffsetDateTime(rs.getTimestamp("updated_at")))
                    .build();

    private static final RowMapper<RankingPostStateRecord> POST_STATE_ROW_MAPPER = (rs, rowNum) ->
            RankingPostStateRecord.builder()
                    .postId(rs.getLong("post_id"))
                    .authorId(getLongOrNull(rs.getObject("author_id")))
                    .publishedAt(toOffsetDateTime(rs.getTimestamp("published_at")))
                    .topicIds(parseTopicIds(rs.getString("topic_ids")))
                    .viewCount(rs.getLong("view_count"))
                    .likeCount(rs.getInt("like_count"))
                    .favoriteCount(rs.getInt("favorite_count"))
                    .commentCount(rs.getInt("comment_count"))
                    .rawScore(rs.getDouble("raw_score"))
                    .hotScore(rs.getDouble("hot_score"))
                    .version(rs.getLong("version"))
                    .lastBucketStart(toOffsetDateTime(rs.getTimestamp("last_bucket_start")))
                    .updatedAt(DateTimeUtils.toOffsetDateTime(rs.getTimestamp("updated_at")))
                    .build();

    private static final RowMapper<SnapshotPostHotState> SNAPSHOT_POST_STATE_ROW_MAPPER = (rs, rowNum) ->
            SnapshotPostHotState.builder()
                    .postId(rs.getLong("post_id"))
                    .authorId(getLongOrNull(rs.getObject("author_id")))
                    .topicIds(parseTopicIds(rs.getString("topic_ids")))
                    .publishedAt(toOffsetDateTime(rs.getTimestamp("published_at")))
                    .viewCount(rs.getLong("view_count"))
                    .likeCount(rs.getInt("like_count"))
                    .favoriteCount(rs.getInt("favorite_count"))
                    .commentCount(rs.getInt("comment_count"))
                    .build();

    private static final RowMapper<SnapshotPeriodScore> PERIOD_SCORE_ROW_MAPPER = (rs, rowNum) ->
            SnapshotPeriodScore.builder()
                    .postId(rs.getLong("post_id"))
                    .deltaScore(rs.getDouble("delta_score"))
                    .build();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void acquireReplayBarrierSharedLock() {
        jdbcTemplate.getJdbcOperations().execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.PreparedStatement statement =
                         connection.prepareStatement("select pg_advisory_xact_lock_shared(?)")) {
                statement.setLong(1, REPLAY_BARRIER_LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }

    public void awaitReplayBarrierDrain() {
        jdbcTemplate.getJdbcOperations().execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.PreparedStatement statement =
                         connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, REPLAY_BARRIER_LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }

    public boolean saveEventAndAccumulateBucket(RankingLedgerEventRecord event, OffsetDateTime bucketStart) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", event.getEventId())
                .addValue("eventType", event.getEventType())
                .addValue("postId", event.getPostId())
                .addValue("actorId", event.getActorId())
                .addValue("authorId", event.getAuthorId())
                .addValue("metricType", event.getMetricType().name())
                .addValue("delta", event.getDelta())
                .addValue("occurredAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(event.getOccurredAt()))
                .addValue("publishedAt", toTimestamp(event.getPublishedAt()))
                .addValue("partitionKey", event.getPartitionKey())
                .addValue("sourceService", event.getSourceService())
                .addValue("sourceOpId", event.getSourceOpId())
                .addValue("createdAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(event.getCreatedAt()));

        int inserted = jdbcTemplate.update("""
                insert into ranking_event_ledger (
                    event_id, event_type, post_id, actor_id, author_id, metric_type,
                    delta, occurred_at, published_at, partition_key, source_service, source_op_id, created_at
                ) values (
                    :eventId, :eventType, :postId, :actorId, :authorId, :metricType,
                    :delta, :occurredAt, :publishedAt, :partitionKey, :sourceService, :sourceOpId, :createdAt
                )
                on conflict (event_id) do nothing
                """, params);
        if (inserted == 0) {
            return false;
        }

        accumulateBucket(event, bucketStart);
        return true;
    }

    public void accumulateBucket(RankingLedgerEventRecord event, OffsetDateTime bucketStart) {
        MapSqlParameterSource bucketParams = new MapSqlParameterSource()
                .addValue("bucketStart", com.zhicore.common.util.DateTimeUtils.toTimestamp(bucketStart))
                .addValue("postId", event.getPostId())
                .addValue("viewDelta", event.getMetricType() == RankingMetricType.VIEW ? event.getDelta() : 0)
                .addValue("likeDelta", event.getMetricType() == RankingMetricType.LIKE ? event.getDelta() : 0)
                .addValue("favoriteDelta", event.getMetricType() == RankingMetricType.FAVORITE ? event.getDelta() : 0)
                .addValue("commentDelta", event.getMetricType() == RankingMetricType.COMMENT ? event.getDelta() : 0)
                .addValue("updatedAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(event.getCreatedAt()));
        jdbcTemplate.update("""
                insert into ranking_delta_bucket (
                    bucket_start, post_id, view_delta, like_delta, favorite_delta, comment_delta,
                    applied_view_delta, applied_like_delta, applied_favorite_delta, applied_comment_delta,
                    flush_owner, flush_started_at, flushed, flushed_at, updated_at
                ) values (
                    :bucketStart, :postId, :viewDelta, :likeDelta, :favoriteDelta, :commentDelta,
                    0, 0, 0, 0,
                    null, null, false, null, :updatedAt
                )
                on conflict (bucket_start, post_id) do update set
                    view_delta = ranking_delta_bucket.view_delta + excluded.view_delta,
                    like_delta = ranking_delta_bucket.like_delta + excluded.like_delta,
                    favorite_delta = ranking_delta_bucket.favorite_delta + excluded.favorite_delta,
                    comment_delta = ranking_delta_bucket.comment_delta + excluded.comment_delta,
                    flushed = false,
                    flushed_at = null,
                    updated_at = excluded.updated_at
                """, bucketParams);
    }

    public List<RankingBucketRecord> claimFlushableBuckets(int limit,
                                                           String flushOwner,
                                                           OffsetDateTime flushStartedAt,
                                                           OffsetDateTime bucketUpperBound,
                                                           OffsetDateTime staleBefore) {
        List<RankingBucketRecord> candidates = jdbcTemplate.query("""
                select bucket_start, post_id, view_delta, like_delta, favorite_delta, comment_delta,
                       applied_view_delta, applied_like_delta, applied_favorite_delta, applied_comment_delta,
                       flush_owner, flush_started_at, flushed_at, updated_at
                from ranking_delta_bucket
                where flushed = false
                  and bucket_start < :bucketUpperBound
                  and (view_delta <> applied_view_delta
                    or like_delta <> applied_like_delta
                    or favorite_delta <> applied_favorite_delta
                    or comment_delta <> applied_comment_delta)
                  and (flush_started_at is null or flush_started_at < :staleBefore)
                order by bucket_start asc, post_id asc
                limit :limit
                """, new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("bucketUpperBound", com.zhicore.common.util.DateTimeUtils.toTimestamp(bucketUpperBound))
                .addValue("staleBefore", com.zhicore.common.util.DateTimeUtils.toTimestamp(staleBefore)), BUCKET_ROW_MAPPER);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankingBucketRecord> claimed = new java.util.ArrayList<>();
        for (RankingBucketRecord candidate : candidates) {
            int updated = jdbcTemplate.update("""
                    update ranking_delta_bucket
                    set flush_owner = :flushOwner,
                        flush_started_at = :flushStartedAt,
                        updated_at = :updatedAt
                    where bucket_start = :bucketStart
                      and post_id = :postId
                      and flushed = false
                      and bucket_start < :bucketUpperBound
                      and (flush_started_at is null or flush_started_at < :staleBefore)
                    """, new MapSqlParameterSource()
                    .addValue("flushOwner", flushOwner)
                    .addValue("flushStartedAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(flushStartedAt))
                    .addValue("updatedAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(flushStartedAt))
                    .addValue("bucketStart", com.zhicore.common.util.DateTimeUtils.toTimestamp(candidate.getBucketStart()))
                    .addValue("postId", candidate.getPostId())
                    .addValue("bucketUpperBound", com.zhicore.common.util.DateTimeUtils.toTimestamp(bucketUpperBound))
                    .addValue("staleBefore", com.zhicore.common.util.DateTimeUtils.toTimestamp(staleBefore)));
            if (updated == 1) {
                claimed.add(candidate.toBuilder()
                        .flushOwner(flushOwner)
                        .flushStartedAt(flushStartedAt)
                        .updatedAt(flushStartedAt)
                        .build());
            }
        }
        return claimed;
    }

    public Map<Long, RankingPostStateRecord> findPostStatesByIds(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RankingPostStateRecord> states = jdbcTemplate.query("""
                select post_id, author_id, published_at, topic_ids, view_count, like_count,
                       favorite_count, comment_count, raw_score, hot_score, version, last_bucket_start, updated_at
                from ranking_post_state
                where post_id in (:postIds)
                """, new MapSqlParameterSource("postIds", postIds), POST_STATE_ROW_MAPPER);
        Map<Long, RankingPostStateRecord> result = new LinkedHashMap<>();
        for (RankingPostStateRecord state : states) {
            result.put(state.getPostId(), state);
        }
        return result;
    }

    public void savePostState(RankingPostStateRecord state) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("postId", state.getPostId())
                .addValue("authorId", state.getAuthorId())
                .addValue("publishedAt", toTimestamp(state.getPublishedAt()))
                .addValue("topicIds", joinTopicIds(state.getTopicIds()))
                .addValue("viewCount", state.getViewCount())
                .addValue("likeCount", state.getLikeCount())
                .addValue("favoriteCount", state.getFavoriteCount())
                .addValue("commentCount", state.getCommentCount())
                .addValue("rawScore", state.getRawScore())
                .addValue("hotScore", state.getHotScore())
                .addValue("version", state.getVersion())
                .addValue("lastBucketStart", toTimestamp(state.getLastBucketStart()))
                .addValue("updatedAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(state.getUpdatedAt()));
        if (state.getVersion() == 0L) {
            int inserted = jdbcTemplate.update("""
                    insert into ranking_post_state (
                        post_id, author_id, published_at, topic_ids, view_count, like_count,
                        favorite_count, comment_count, raw_score, hot_score, version, last_bucket_start, updated_at
                    ) values (
                        :postId, :authorId, :publishedAt, :topicIds, :viewCount, :likeCount,
                        :favoriteCount, :commentCount, :rawScore, :hotScore, :version, :lastBucketStart, :updatedAt
                    )
                    on conflict (post_id) do nothing
                    """, params);
            if (inserted == 1) {
                return;
            }
            throw new IllegalStateException("插入 ranking_post_state 失败，可能存在并发写入: postId=%s".formatted(state.getPostId()));
        }

        int updated = jdbcTemplate.update("""
                update ranking_post_state
                set author_id = :authorId,
                    published_at = :publishedAt,
                    topic_ids = :topicIds,
                    view_count = :viewCount,
                    like_count = :likeCount,
                    favorite_count = :favoriteCount,
                    comment_count = :commentCount,
                    raw_score = :rawScore,
                    hot_score = :hotScore,
                    version = :version,
                    last_bucket_start = :lastBucketStart,
                    updated_at = :updatedAt
                where post_id = :postId
                  and version = :expectedVersion
                """, params.addValue("expectedVersion", state.getVersion() - 1));
        if (updated != 1) {
            throw new IllegalStateException("更新 ranking_post_state 失败，version 不匹配: postId=%s, version=%s"
                    .formatted(state.getPostId(), state.getVersion()));
        }
    }

    public void incrementPeriodScore(String periodType, String periodKey, Long postId, double deltaScore, OffsetDateTime updatedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("periodType", periodType)
                .addValue("periodKey", periodKey)
                .addValue("postId", postId)
                .addValue("deltaScore", deltaScore)
                .addValue("updatedAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(updatedAt));
        jdbcTemplate.update("""
                insert into ranking_period_score (period_type, period_key, post_id, delta_score, updated_at)
                values (:periodType, :periodKey, :postId, :deltaScore, :updatedAt)
                on conflict (period_type, period_key, post_id) do update set
                    delta_score = ranking_period_score.delta_score + excluded.delta_score,
                    updated_at = excluded.updated_at
                """, params);
    }

    public void markBucketFlushed(RankingBucketRecord bucket, String flushOwner, OffsetDateTime updatedAt) {
        int updated = jdbcTemplate.update("""
                update ranking_delta_bucket
                set applied_view_delta = :appliedViewDelta,
                    applied_like_delta = :appliedLikeDelta,
                    applied_favorite_delta = :appliedFavoriteDelta,
                    applied_comment_delta = :appliedCommentDelta,
                    flushed = view_delta = :appliedViewDelta
                        and like_delta = :appliedLikeDelta
                        and favorite_delta = :appliedFavoriteDelta
                        and comment_delta = :appliedCommentDelta,
                    flushed_at = case
                        when view_delta = :appliedViewDelta
                         and like_delta = :appliedLikeDelta
                         and favorite_delta = :appliedFavoriteDelta
                         and comment_delta = :appliedCommentDelta
                        then :updatedAt
                        else cast(null as timestamp)
                    end,
                    flush_owner = null,
                    flush_started_at = null,
                    updated_at = :updatedAt
                where bucket_start = :bucketStart
                  and post_id = :postId
                  and flush_owner = :flushOwner
                """, new MapSqlParameterSource()
                .addValue("bucketStart", com.zhicore.common.util.DateTimeUtils.toTimestamp(bucket.getBucketStart()))
                .addValue("postId", bucket.getPostId())
                .addValue("flushOwner", flushOwner)
                .addValue("appliedViewDelta", bucket.getViewDelta())
                .addValue("appliedLikeDelta", bucket.getLikeDelta())
                .addValue("appliedFavoriteDelta", bucket.getFavoriteDelta())
                .addValue("appliedCommentDelta", bucket.getCommentDelta())
                .addValue("updatedAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(updatedAt)));
        if (updated != 1) {
            throw new IllegalStateException("标记 bucket flush 完成失败: bucketStart=%s, postId=%s"
                    .formatted(bucket.getBucketStart(), bucket.getPostId()));
        }
    }

    public void resetMaterializedState() {
        jdbcTemplate.getJdbcOperations().update("delete from ranking_period_score");
        jdbcTemplate.getJdbcOperations().update("delete from ranking_post_state");
        jdbcTemplate.getJdbcOperations().update("delete from ranking_delta_bucket");
    }

    public List<RankingLedgerEventRecord> listLedgerEventsAfter(OffsetDateTime afterOccurredAt,
                                                                String afterEventId,
                                                                int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit);
        StringBuilder sql = new StringBuilder("""
                select event_id, event_type, post_id, actor_id, author_id, metric_type, delta,
                       occurred_at, published_at, partition_key, source_service, source_op_id, created_at
                from ranking_event_ledger
                """);
        if (afterOccurredAt != null) {
            sql.append("""
                    where occurred_at > :afterOccurredAt
                       or (occurred_at = :afterOccurredAt and event_id > :afterEventId)
                    """);
            params.addValue("afterOccurredAt", com.zhicore.common.util.DateTimeUtils.toTimestamp(afterOccurredAt));
            params.addValue("afterEventId", afterEventId == null ? "" : afterEventId);
        }
        sql.append("""
                order by occurred_at asc, event_id asc
                limit :limit
                """);
        return jdbcTemplate.query(sql.toString(), params, LEDGER_EVENT_ROW_MAPPER);
    }

    public List<SnapshotPostHotState> listSnapshotPostStates() {
        return jdbcTemplate.query("""
                select post_id, author_id, published_at, topic_ids, view_count, like_count, favorite_count, comment_count
                from ranking_post_state
                """, SNAPSHOT_POST_STATE_ROW_MAPPER);
    }

    public List<SnapshotPeriodScore> listPeriodScores(String periodType, String periodKey) {
        return jdbcTemplate.query("""
                select post_id, delta_score
                from ranking_period_score
                where period_type = :periodType and period_key = :periodKey
                order by delta_score desc, post_id asc
                """, new MapSqlParameterSource()
                .addValue("periodType", periodType)
                .addValue("periodKey", periodKey), PERIOD_SCORE_ROW_MAPPER);
    }

    private static Long getLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp value) {
        return DateTimeUtils.toOffsetDateTime(value);
    }

    private static Timestamp toTimestamp(OffsetDateTime value) {
        return value != null ? com.zhicore.common.util.DateTimeUtils.toTimestamp(value) : null;
    }

    private static List<Long> parseTopicIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(raw.split(","))
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .map(Long::valueOf)
                .toList();
    }

    private static String joinTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return null;
        }
        return topicIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
