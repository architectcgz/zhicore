package com.zhicore.content.interfaces.controller.admin;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxRetryAuditMapper;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxRetryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Outbox 管理端接口集成测试（R14）
 *
 * 覆盖：失败列表查询、手动重试、429 限频、边界处理
 */
@AutoConfigureMockMvc
@DisplayName("Outbox 管理端接口集成测试")
class OutboxAdminControllerIntegrationTest extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/outbox";
    private static final String OPERATOR = "9001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private OutboxRetryAuditMapper outboxRetryAuditMapper;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    // ==================== 11.1 失败列表查询测试 ====================

    @Nested
    @DisplayName("失败列表查询")
    class FailedListTests {

        @Test
        @DisplayName("仅返回 FAILED，支持按 eventType 过滤")
        void listFailedWithFilter() throws Exception {
            insertFailed("E1", "TYPE_A");
            insertFailed("E2", "TYPE_B");

            mockMvc.perform(get(BASE + "/failed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.items", hasSize(2)));

            mockMvc.perform(get(BASE + "/failed").param("eventType", "TYPE_A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].eventType").value("TYPE_A"));
        }

        @Test
        @DisplayName("无失败事件时返回空列表")
        void emptyListWhenNoFailedEvents() throws Exception {
            mockMvc.perform(get(BASE + "/failed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.items", hasSize(0)));
        }

        @Test
        @DisplayName("非 FAILED 状态的事件不出现在失败列表中")
        void nonFailedEventsShouldNotAppear() throws Exception {
            insertFailed("E_FAIL", "TYPE_A");
            // 插入一个 PENDING 状态的事件
            insertWithStatus("E_PENDING", "TYPE_A", OutboxEventEntity.OutboxStatus.PENDING);

            mockMvc.perform(get(BASE + "/failed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].eventId").value("E_FAIL"));
        }
    }

    // ==================== 11.2 & 11.3 手动重试与限频测试 ====================

    @Nested
    @DisplayName("手动重试与限频")
    class RetryTests {

        @Test
        @DisplayName("第一次重试成功，状态变为 PENDING，写入审计记录")
        void retrySuccessShouldChangeToPendingAndAudit() throws Exception {
            insertFailed("E3", "TYPE_A");

            OutboxRetryRequest request = new OutboxRetryRequest();
            request.setReason("手动重试验证");

            mockMvc.perform(
                            post(BASE + "/{eventId}/retry", "E3")
                                    .header(CommonConstants.HEADER_USER_ID, OPERATOR)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.eventId").value("E3"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));

            // 审计至少 1 条
            long audits = outboxRetryAuditMapper.countRecentRetries(
                    "E3", Instant.now().minusSeconds(3600));
            org.junit.jupiter.api.Assertions.assertTrue(
                    audits >= 1, "应写入手动重试审计记录");
        }

        @Test
        @DisplayName("10 分钟内重复重试同一事件返回 429")
        void duplicateRetryWithinWindowShouldReturn429() throws Exception {
            insertFailed("E4", "TYPE_B");

            OutboxRetryRequest request = new OutboxRetryRequest();
            request.setReason("限频测试");

            // 第一次重试成功
            mockMvc.perform(
                            post(BASE + "/{eventId}/retry", "E4")
                                    .header(CommonConstants.HEADER_USER_ID, OPERATOR)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk());

            // 10 分钟内第二次重试应 429
            mockMvc.perform(
                            post(BASE + "/{eventId}/retry", "E4")
                                    .header(CommonConstants.HEADER_USER_ID, OPERATOR)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(429));
        }

        @Test
        @DisplayName("不同事件的重试互不影响")
        void retryDifferentEventsShouldNotInterfere() throws Exception {
            insertFailed("E5", "TYPE_A");
            insertFailed("E6", "TYPE_B");

            OutboxRetryRequest request = new OutboxRetryRequest();
            request.setReason("独立性测试");

            // 重试 E5
            mockMvc.perform(
                            post(BASE + "/{eventId}/retry", "E5")
                                    .header(CommonConstants.HEADER_USER_ID, OPERATOR)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk());

            // E6 不受 E5 限频影响
            mockMvc.perform(
                            post(BASE + "/{eventId}/retry", "E6")
                                    .header(CommonConstants.HEADER_USER_ID, OPERATOR)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isOk());
        }
    }

    // ==================== 辅助方法 ====================

    private void insertFailed(String eventId, String eventType) {
        insertWithStatus(eventId, eventType, OutboxEventEntity.OutboxStatus.FAILED);
    }

    private void insertWithStatus(String eventId, String eventType, OutboxEventEntity.OutboxStatus status) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateId(1L);
        entity.setAggregateVersion(1L);
        entity.setSchemaVersion(1);
        entity.setPayload("{\"k\":\"v\"}");
        entity.setOccurredAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setRetryCount(1);
        entity.setLastError("test error");
        entity.setStatus(status);
        outboxEventMapper.insert(entity);
    }
}
