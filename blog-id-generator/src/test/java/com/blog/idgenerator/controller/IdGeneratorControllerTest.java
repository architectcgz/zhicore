package com.blog.idgenerator.controller;

import com.blog.common.exception.BusinessException;
import com.blog.idgenerator.service.IdGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ID生成服务控制器测试
 * 
 * 使用 @WebMvcTest 测试 Controller 层
 */
@WebMvcTest(controllers = IdGeneratorController.class)
@Import(com.blog.common.exception.GlobalExceptionHandler.class)
@ActiveProfiles("test")
@DisplayName("ID生成服务控制器测试")
class IdGeneratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdGeneratorService idGeneratorService;

    @Test
    @DisplayName("应该成功生成单个Snowflake ID")
    void shouldGenerateSingleSnowflakeId() throws Exception {
        // Given
        Long expectedId = 1234567890123456789L;
        when(idGeneratorService.generateSnowflakeId()).thenReturn(expectedId);

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value(expectedId))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("应该成功批量生成Snowflake ID")
    void shouldGenerateBatchSnowflakeIds() throws Exception {
        // Given
        int count = 5;
        List<Long> expectedIds = Arrays.asList(
                1234567890123456789L,
                1234567890123456790L,
                1234567890123456791L,
                1234567890123456792L,
                1234567890123456793L
        );
        when(idGeneratorService.generateBatchSnowflakeIds(count)).thenReturn(expectedIds);

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch")
                        .param("count", String.valueOf(count)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(count)))
                .andExpect(jsonPath("$.data[0]").value(expectedIds.get(0)))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("应该成功生成Segment ID")
    void shouldGenerateSegmentId() throws Exception {
        // Given
        String bizTag = "user";
        Long expectedId = 1000L;
        when(idGeneratorService.generateSegmentId(bizTag)).thenReturn(expectedId);

        // When & Then
        mockMvc.perform(get("/api/v1/id/segment/{bizTag}", bizTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value(expectedId))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("应该拒绝无效的批量数量 - count为0")
    void shouldRejectInvalidBatchCount_Zero() throws Exception {
        // Given
        int invalidCount = 0;
        when(idGeneratorService.generateBatchSnowflakeIds(invalidCount))
                .thenThrow(new IllegalArgumentException("生成数量必须在1-1000之间"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch")
                        .param("count", String.valueOf(invalidCount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数值无效"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该拒绝无效的批量数量 - count为负数")
    void shouldRejectInvalidBatchCount_Negative() throws Exception {
        // Given
        int invalidCount = -5;
        when(idGeneratorService.generateBatchSnowflakeIds(invalidCount))
                .thenThrow(new IllegalArgumentException("生成数量必须在1-1000之间"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch")
                        .param("count", String.valueOf(invalidCount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数值无效"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该拒绝无效的批量数量 - count超过1000")
    void shouldRejectInvalidBatchCount_TooLarge() throws Exception {
        // Given
        int invalidCount = 1001;
        when(idGeneratorService.generateBatchSnowflakeIds(invalidCount))
                .thenThrow(new IllegalArgumentException("生成数量必须在1-1000之间"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch")
                        .param("count", String.valueOf(invalidCount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数值无效"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该拒绝空的业务标签")
    void shouldRejectEmptyBizTag() throws Exception {
        // Given
        String bizTag = "  "; // Use whitespace instead of empty string to avoid 404
        when(idGeneratorService.generateSegmentId(anyString()))
                .thenThrow(new IllegalArgumentException("业务标签不能为空"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/segment/{bizTag}", bizTag))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("参数值无效"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该处理ID生成失败异常 - Snowflake")
    void shouldHandleIdGenerationFailure_Snowflake() throws Exception {
        // Given
        when(idGeneratorService.generateSnowflakeId())
                .thenThrow(new BusinessException("ID生成失败: 连接超时"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("ID生成失败: 连接超时"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该处理ID生成失败异常 - Batch")
    void shouldHandleIdGenerationFailure_Batch() throws Exception {
        // Given
        int count = 10;
        when(idGeneratorService.generateBatchSnowflakeIds(count))
                .thenThrow(new BusinessException("批量ID生成失败: 服务不可用"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch")
                        .param("count", String.valueOf(count)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("批量ID生成失败: 服务不可用"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该处理ID生成失败异常 - Segment")
    void shouldHandleIdGenerationFailure_Segment() throws Exception {
        // Given
        String bizTag = "user";
        when(idGeneratorService.generateSegmentId(bizTag))
                .thenThrow(new BusinessException("ID生成失败: 数据库连接失败"));

        // When & Then
        mockMvc.perform(get("/api/v1/id/segment/{bizTag}", bizTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("ID生成失败: 数据库连接失败"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该处理缺少必需参数异常")
    void shouldHandleMissingRequiredParameter() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(containsString("count")))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("应该处理参数类型不匹配异常")
    void shouldHandleTypeMismatchException() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/id/snowflake/batch")
                        .param("count", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.success").value(false));
    }
}
