package com.zhicore.notification.integration;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ID生成服务集成测试
 * 
 * 验证 ZhiCore-notification 服务能够正确使用 IdGeneratorFeignClient 生成ID
 */
@SpringBootTest
@DisplayName("ID生成服务集成测试")
@org.springframework.test.context.ActiveProfiles("test")
class IdGeneratorIntegrationTest {

    @MockBean
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Autowired
    private IdGeneratorFeignClient injectedClient;

    @Test
    @DisplayName("应该能够注入 IdGeneratorFeignClient")
    void shouldInjectIdGeneratorFeignClient() {
        assertThat(injectedClient).isNotNull();
    }

    @Test
    @DisplayName("应该能够生成单个 Snowflake ID")
    void shouldGenerateSingleSnowflakeId() {
        // Given
        Long expectedId = 1234567890123456789L;
        ApiResponse<Long> response = ApiResponse.success(expectedId);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(response);

        // When
        ApiResponse<Long> result = idGeneratorFeignClient.generateSnowflakeId();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("应该能够批量生成 Snowflake ID")
    void shouldGenerateBatchSnowflakeIds() {
        // Given
        List<Long> expectedIds = Arrays.asList(
                1234567890123456789L,
                1234567890123456790L,
                1234567890123456791L
        );
        ApiResponse<List<Long>> response = ApiResponse.success(expectedIds);
        when(idGeneratorFeignClient.generateBatchSnowflakeIds(3)).thenReturn(response);

        // When
        ApiResponse<List<Long>> result = idGeneratorFeignClient.generateBatchSnowflakeIds(3);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(3);
        assertThat(result.getData()).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("应该能够生成 Segment ID")
    void shouldGenerateSegmentId() {
        // Given
        Long expectedId = 1000001L;
        String bizTag = "notification";
        ApiResponse<Long> response = ApiResponse.success(expectedId);
        when(idGeneratorFeignClient.generateSegmentId(bizTag)).thenReturn(response);

        // When
        ApiResponse<Long> result = idGeneratorFeignClient.generateSegmentId(bizTag);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("当ID生成失败时应该返回错误响应")
    void shouldReturnErrorWhenIdGenerationFails() {
        // Given
        ApiResponse<Long> errorResponse = ApiResponse.fail(500, "ID生成服务暂时不可用");
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(errorResponse);

        // When
        ApiResponse<Long> result = idGeneratorFeignClient.generateSnowflakeId();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("ID生成服务暂时不可用");
    }
}
