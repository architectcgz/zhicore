package com.zhicore.ranking.infrastructure.pg;

import com.zhicore.ranking.application.model.RankingBucketRecord;
import com.zhicore.ranking.application.model.RankingLedgerEventRecord;
import com.zhicore.ranking.domain.model.RankingMetricType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@DisplayName("PgRankingLedgerRepository PostgreSQL Tests")
class PgRankingLedgerRepositoryPostgresTest {

    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withEnv("POSTGRES_DB", "zhicore_ranking_test")
            .withEnv("POSTGRES_USER", "postgres")
            .withEnv("POSTGRES_PASSWORD", "postgres")
            .withExposedPorts(5432)
            .withReuse(true);

    private static NamedParameterJdbcTemplate jdbcTemplate;
    private static PgRankingLedgerRepository repository;

    @BeforeAll
    static void setUpRepository() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:postgresql://%s:%d/zhicore_ranking_test".formatted(postgres.getHost(), postgres.getFirstMappedPort()),
                "postgres",
                "postgres"
        );
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new PgRankingLedgerRepository(jdbcTemplate);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/ranking-schema.sql"));
        }
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.getJdbcOperations().update("delete from ranking_period_score");
        jdbcTemplate.getJdbcOperations().update("delete from ranking_post_state");
        jdbcTemplate.getJdbcOperations().update("delete from ranking_delta_bucket");
        jdbcTemplate.getJdbcOperations().update("delete from ranking_event_ledger");
    }

    @Test
    @DisplayName("listLedgerEventsAfter should support null cursor on PostgreSQL")
    void listLedgerEventsAfterShouldSupportNullCursorOnPostgreSql() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 10, 20, 0);
        RankingLedgerEventRecord first = event("it-liked-1", RankingMetricType.LIKE, 1, occurredAt);
        RankingLedgerEventRecord second = event("it-liked-2", RankingMetricType.LIKE, 1, occurredAt.plusSeconds(1));
        RankingLedgerEventRecord third = event("it-viewed-1", RankingMetricType.VIEW, 1, occurredAt.plusSeconds(2));

        repository.saveEventAndAccumulateBucket(first, occurredAt.withSecond(0).withNano(0));
        repository.saveEventAndAccumulateBucket(second, occurredAt.plusSeconds(1).withSecond(0).withNano(0));
        repository.saveEventAndAccumulateBucket(third, occurredAt.plusSeconds(2).withSecond(0).withNano(0));

        List<RankingLedgerEventRecord> firstBatch = repository.listLedgerEventsAfter(null, null, 2);
        List<RankingLedgerEventRecord> secondBatch =
                repository.listLedgerEventsAfter(second.getOccurredAt(), second.getEventId(), 2);

        assertEquals(List.of("it-liked-1", "it-liked-2"),
                firstBatch.stream().map(RankingLedgerEventRecord::getEventId).toList());
        assertEquals(List.of("it-viewed-1"),
                secondBatch.stream().map(RankingLedgerEventRecord::getEventId).toList());
    }

    @Test
    @DisplayName("late arrival on flushed bucket should only expose pending delta on next claim")
    void lateArrivalOnFlushedBucketShouldOnlyExposePendingDelta() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 10, 20, 0);
        LocalDateTime bucketStart = occurredAt.withSecond(0).withNano(0);
        RankingLedgerEventRecord first = event("it-liked-1", RankingMetricType.LIKE, 1, occurredAt);
        RankingLedgerEventRecord second = event("it-viewed-1", RankingMetricType.VIEW, 1, occurredAt.plusSeconds(1));

        repository.saveEventAndAccumulateBucket(first, bucketStart);
        repository.saveEventAndAccumulateBucket(second, bucketStart);

        List<RankingBucketRecord> firstClaim = repository.claimFlushableBuckets(
                10,
                "owner-a",
                occurredAt.plusMinutes(1),
                occurredAt.plusMinutes(2),
                occurredAt.minusMinutes(1)
        );
        assertEquals(1, firstClaim.size());
        RankingBucketRecord firstBucket = firstClaim.get(0);
        assertEquals(1L, firstBucket.pendingViewDelta());
        assertEquals(1, firstBucket.pendingLikeDelta());

        repository.markBucketFlushed(firstBucket, "owner-a", occurredAt.plusMinutes(1));

        Map<String, Object> flushedRow = jdbcTemplate.getJdbcOperations().queryForMap("""
                select view_delta, like_delta, applied_view_delta, applied_like_delta, flushed
                from ranking_delta_bucket
                where bucket_start = ? and post_id = ?
                """, Timestamp.valueOf(bucketStart), 9001L);
        assertEquals(1L, ((Number) flushedRow.get("view_delta")).longValue());
        assertEquals(1, ((Number) flushedRow.get("like_delta")).intValue());
        assertEquals(1L, ((Number) flushedRow.get("applied_view_delta")).longValue());
        assertEquals(1, ((Number) flushedRow.get("applied_like_delta")).intValue());
        assertTrue((Boolean) flushedRow.get("flushed"));

        RankingLedgerEventRecord late = event("it-viewed-2", RankingMetricType.VIEW, 1, occurredAt.plusSeconds(2));
        repository.saveEventAndAccumulateBucket(late, bucketStart);

        Map<String, Object> lateRow = jdbcTemplate.getJdbcOperations().queryForMap("""
                select view_delta, like_delta, applied_view_delta, applied_like_delta, flushed, flush_owner
                from ranking_delta_bucket
                where bucket_start = ? and post_id = ?
                """, Timestamp.valueOf(bucketStart), 9001L);
        assertEquals(2L, ((Number) lateRow.get("view_delta")).longValue());
        assertEquals(1L, ((Number) lateRow.get("applied_view_delta")).longValue());
        assertFalse((Boolean) lateRow.get("flushed"));
        assertNull(lateRow.get("flush_owner"));

        List<RankingBucketRecord> secondClaim = repository.claimFlushableBuckets(
                10,
                "owner-b",
                occurredAt.plusMinutes(3),
                occurredAt.plusMinutes(4),
                occurredAt.plusMinutes(1)
        );
        assertEquals(1, secondClaim.size());
        RankingBucketRecord secondBucket = secondClaim.get(0);
        assertEquals(1L, secondBucket.pendingViewDelta());
        assertEquals(0, secondBucket.pendingLikeDelta());
        assertEquals(2L, secondBucket.getViewDelta());
        assertEquals(1L, secondBucket.getAppliedViewDelta());
    }

    private static RankingLedgerEventRecord event(String eventId,
                                                  RankingMetricType metricType,
                                                  int delta,
                                                  LocalDateTime occurredAt) {
        return RankingLedgerEventRecord.builder()
                .eventId(eventId)
                .eventType(metricType.name())
                .postId(9001L)
                .actorId(3001L)
                .authorId(4001L)
                .metricType(metricType)
                .delta(delta)
                .occurredAt(occurredAt)
                .publishedAt(occurredAt.minusHours(1))
                .partitionKey("post:9001")
                .sourceService("it-test")
                .sourceOpId(eventId)
                .createdAt(occurredAt.plusSeconds(1))
                .build();
    }
}
