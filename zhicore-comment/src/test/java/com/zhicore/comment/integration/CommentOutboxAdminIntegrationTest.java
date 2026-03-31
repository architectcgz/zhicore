package com.zhicore.comment.integration;

import com.zhicore.common.constant.CommonConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Comment outbox 管理接口集成测试")
class CommentOutboxAdminIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
    }

    @Test
    @DisplayName("应基于 PostgreSQL 返回 outbox 摘要统计")
    void shouldReturnSummaryFromPostgres() throws Exception {
        insertOutboxEvent("evt-pending-1", "PENDING", 0, 10, "2026-03-16 10:00:00", "2026-03-16 10:00:00");
        insertOutboxEvent("evt-pending-2", "PENDING", 0, 10, "2026-03-16 10:05:00", "2026-03-16 10:05:00");
        insertOutboxEvent("evt-failed-1", "FAILED", 1, 10, "2026-03-16 10:10:00", "2026-03-16 10:11:00");
        insertOutboxEvent("evt-dead-1", "DEAD", 10, 10, "2026-03-16 10:20:00", "2026-03-16 10:21:00");
        insertOutboxEvent("evt-succeeded-1", "SUCCEEDED", 0, 10, "2026-03-16 10:30:00", "2026-03-16 10:31:00");

        mockMvc.perform(get("/api/v1/admin/comment-outbox/summary")
                        .header(CommonConstants.HEADER_USER_ID, "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.deadCount").value(1))
                .andExpect(jsonPath("$.data.succeededCount").value(1))
                .andExpect(jsonPath("$.data.oldestPendingCreatedAt").value("2026-03-16T10:00:00+08:00"));
    }

    @Test
    @DisplayName("批量重试 dead 后应将记录重置为 pending")
    void shouldRetryDeadEventsInPostgres() throws Exception {
        insertOutboxEvent("evt-dead-1", "DEAD", 10, 10, "2026-03-16 10:20:00", "2026-03-16 10:21:00");
        insertOutboxEvent("evt-dead-2", "DEAD", 10, 10, "2026-03-16 10:22:00", "2026-03-16 10:23:00");

        mockMvc.perform(post("/api/v1/admin/comment-outbox/retry-dead")
                        .header(CommonConstants.HEADER_USER_ID, "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.retriedCount").value(2))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.deadCount").value(0));

        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'",
                Integer.class
        );
        Integer deadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'DEAD'",
                Integer.class
        );
        Integer resetRetryCount = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM outbox_events WHERE id = 'evt-dead-1'",
                Integer.class
        );

        org.junit.jupiter.api.Assertions.assertEquals(2, pendingCount);
        org.junit.jupiter.api.Assertions.assertEquals(0, deadCount);
        org.junit.jupiter.api.Assertions.assertEquals(0, resetRetryCount);
    }

    private void insertOutboxEvent(String id,
                                   String status,
                                   int retryCount,
                                   int maxRetries,
                                   String createdAt,
                                   String updatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO outbox_events (
                            id, topic, tag, sharding_key, payload, status,
                            retry_count, max_retries, next_attempt_at,
                            created_at, updated_at, sent_at, error_message,
                            claimed_by, claimed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                "comment-topic",
                "comment-tag",
                "2001",
                "{\"event\":\"comment\"}",
                status,
                retryCount,
                maxRetries,
                java.sql.Timestamp.valueOf(createdAt),
                java.sql.Timestamp.valueOf(createdAt),
                java.sql.Timestamp.valueOf(updatedAt),
                "SUCCEEDED".equals(status) ? java.sql.Timestamp.valueOf(updatedAt) : null,
                "DEAD".equals(status) ? "mq error" : null,
                null,
                null
        );
    }
}
