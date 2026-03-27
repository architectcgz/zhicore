package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.model.OutboxEvent;
import com.zhicore.comment.domain.model.OutboxEventStatus;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 评论服务 outbox JDBC 仓储实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private static final String FAILURE_OBSERVED_AT_SQL = "COALESCE(next_attempt_at, created_at)";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final RowMapper<OutboxEvent> ROW_MAPPER = new RowMapper<>() {
        @Override
        public OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            OutboxEvent event = new OutboxEvent();
            event.setId(rs.getString("id"));
            event.setTopic(rs.getString("topic"));
            event.setTag(rs.getString("tag"));
            event.setShardingKey(rs.getString("sharding_key"));
            event.setPayload(rs.getString("payload"));
            event.setStatus(OutboxEventStatus.fromStorageValue(rs.getString("status")));
            event.setRetryCount(rs.getInt("retry_count"));
            event.setMaxRetries(rs.getInt("max_retries"));

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                event.setCreatedAt(createdAt.toLocalDateTime());
            }

            Timestamp sentAt = rs.getTimestamp("sent_at");
            if (sentAt != null) {
                event.setSentAt(sentAt.toLocalDateTime());
            }

            Timestamp nextAttemptAt = rs.getTimestamp("next_attempt_at");
            if (nextAttemptAt != null) {
                event.setNextAttemptAt(nextAttemptAt.toLocalDateTime());
            }

            event.setErrorMessage(rs.getString("error_message"));
            Timestamp claimedAt = rs.getTimestamp("claimed_at");
            if (claimedAt != null) {
                event.setClaimedAt(claimedAt.toLocalDateTime());
            }
            event.setClaimedBy(rs.getString("claimed_by"));
            return event;
        }
    };

    @Override
    public void save(OutboxEvent event) {
        String sql = """
            INSERT INTO outbox_events (
                id, topic, tag, sharding_key, payload, status,
                retry_count, max_retries, next_attempt_at, created_at, sent_at, error_message,
                claimed_by, claimed_at
            ) VALUES (
                :id, :topic, :tag, :shardingKey, :payload, :status,
                :retryCount, :maxRetries, :nextAttemptAt, :createdAt, :sentAt, :errorMessage,
                :claimedBy, :claimedAt
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", event.getId())
                .addValue("topic", event.getTopic())
                .addValue("tag", event.getTag())
                .addValue("shardingKey", event.getShardingKey())
                .addValue("payload", event.getPayload())
                .addValue("status", event.getStatus().name())
                .addValue("retryCount", event.getRetryCount())
                .addValue("maxRetries", event.getMaxRetries())
                .addValue("nextAttemptAt", event.getNextAttemptAt() != null ? Timestamp.valueOf(event.getNextAttemptAt()) : null)
                .addValue("createdAt", Timestamp.valueOf(event.getCreatedAt()))
                .addValue("sentAt", event.getSentAt() != null ? Timestamp.valueOf(event.getSentAt()) : null)
                .addValue("errorMessage", event.getErrorMessage())
                .addValue("claimedBy", event.getClaimedBy())
                .addValue("claimedAt", event.getClaimedAt() != null ? Timestamp.valueOf(event.getClaimedAt()) : null);

        namedParameterJdbcTemplate.update(sql, params);
    }

    @Override
    public void update(OutboxEvent event) {
        String sql = """
            UPDATE outbox_events
            SET status = :status,
                retry_count = :retryCount,
                max_retries = :maxRetries,
                next_attempt_at = :nextAttemptAt,
                sent_at = :sentAt,
                error_message = :errorMessage,
                claimed_by = :claimedBy,
                claimed_at = :claimedAt
            WHERE id = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", event.getId())
                .addValue("status", event.getStatus().name())
                .addValue("retryCount", event.getRetryCount())
                .addValue("maxRetries", event.getMaxRetries())
                .addValue("nextAttemptAt", event.getNextAttemptAt() != null ? Timestamp.valueOf(event.getNextAttemptAt()) : null)
                .addValue("sentAt", event.getSentAt() != null ? Timestamp.valueOf(event.getSentAt()) : null)
                .addValue("errorMessage", event.getErrorMessage())
                .addValue("claimedBy", event.getClaimedBy())
                .addValue("claimedAt", event.getClaimedAt() != null ? Timestamp.valueOf(event.getClaimedAt()) : null);

        namedParameterJdbcTemplate.update(sql, params);
    }

    @Override
    public Optional<OutboxEvent> findById(String id) {
        String sql = """
            SELECT *
            FROM outbox_events
            WHERE id = :id
            """;

        List<OutboxEvent> results = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("id", id),
                ROW_MAPPER
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, int limit) {
        String sql = """
            SELECT *
            FROM outbox_events
            WHERE status = :status
            ORDER BY created_at ASC
            LIMIT :limit
            """;

        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("status", status.name()).addValue("limit", limit),
                ROW_MAPPER
        );
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxEventStatus status) {
        String sql = """
            SELECT *
            FROM outbox_events
            WHERE status = :status
            ORDER BY created_at ASC
            """;

        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("status", status.name()),
                ROW_MAPPER
        );
    }

    @Override
    public long countByStatus(OutboxEventStatus status) {
        String sql = """
            SELECT COUNT(*)
            FROM outbox_events
            WHERE status = :status
            """;

        Long count = namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource().addValue("status", status.name()),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public java.time.LocalDateTime findOldestPendingCreatedAt() {
        String sql = """
            SELECT MIN(created_at)
            FROM outbox_events
            WHERE status = 'PENDING'
            """;

        Timestamp timestamp = namedParameterJdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Timestamp.class);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public long countSucceededSince(java.time.LocalDateTime since) {
        String sql = """
            SELECT COUNT(*)
            FROM outbox_events
            WHERE status IN ('SUCCEEDED', 'SENT')
              AND sent_at >= :since
            """;

        Long count = namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource().addValue("since", Timestamp.valueOf(since)),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public long countFailedSince(java.time.LocalDateTime since, int defaultMaxRetries) {
        String sql = ("""
            SELECT COUNT(*)
            FROM outbox_events
            WHERE status = 'FAILED'
              AND COALESCE(retry_count, 0) < COALESCE(max_retries, :defaultMaxRetries)
              AND %s >= :since
            """).formatted(FAILURE_OBSERVED_AT_SQL);

        Long count = namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("since", Timestamp.valueOf(since))
                        .addValue("defaultMaxRetries", defaultMaxRetries),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public long countDeadSince(java.time.LocalDateTime since, int defaultMaxRetries) {
        String sql = ("""
            SELECT COUNT(*)
            FROM outbox_events
            WHERE (
                    status = 'DEAD'
                 OR (
                        status = 'FAILED'
                    AND COALESCE(retry_count, 0) >= COALESCE(max_retries, :defaultMaxRetries)
                 )
                  )
              AND %s >= :since
            """).formatted(FAILURE_OBSERVED_AT_SQL);

        Long count = namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("since", Timestamp.valueOf(since))
                        .addValue("defaultMaxRetries", defaultMaxRetries),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public List<OutboxEvent> claimRetryableEvents(java.time.LocalDateTime now,
                                                  java.time.LocalDateTime reclaimBefore,
                                                  String claimedBy,
                                                  int limit) {
        String sql = """
            WITH head_per_sharding_key AS (
                SELECT DISTINCT ON (COALESCE(sharding_key, id))
                    id
                FROM outbox_events
                WHERE status NOT IN ('SUCCEEDED', 'SENT')
                ORDER BY COALESCE(sharding_key, id), created_at ASC, id ASC
            ),
            claimed AS (
                SELECT event.id
                FROM outbox_events event
                JOIN head_per_sharding_key head ON head.id = event.id
                WHERE (
                        event.status IN ('PENDING', 'FAILED')
                        AND (event.next_attempt_at IS NULL OR event.next_attempt_at <= :now)
                    )
                   OR (
                        event.status = 'PROCESSING'
                        AND event.claimed_at IS NOT NULL
                        AND event.claimed_at <= :reclaimBefore
                    )
                ORDER BY event.created_at ASC, event.id ASC
                LIMIT :limit
                FOR UPDATE OF event SKIP LOCKED
            )
            UPDATE outbox_events target
            SET status = 'PROCESSING',
                claimed_by = :claimedBy,
                claimed_at = :now
            FROM claimed
            WHERE target.id = claimed.id
            RETURNING target.*
            """;

        List<OutboxEvent> events = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("now", Timestamp.valueOf(now))
                        .addValue("reclaimBefore", Timestamp.valueOf(reclaimBefore))
                        .addValue("claimedBy", claimedBy)
                        .addValue("limit", limit),
                ROW_MAPPER
        );
        log.debug("claim 评论服务可投递 outbox 事件: workerId={}, limit={}, found={}",
                claimedBy, limit, events.size());
        return events;
    }
}
