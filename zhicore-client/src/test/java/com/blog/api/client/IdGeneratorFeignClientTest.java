package com.zhicore.api.client;

import com.zhicore.api.client.fallback.IdGeneratorFeignClientFallbackFactory;
import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * IdGeneratorFeignClient 单元测试
 * 
 * 测试 Feign 接口定义的正确性
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ID生成服务 Feign 客户端测试")
class IdGeneratorFeignClientTest {

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Test
    @DisplayName("测试 generateSnowflakeId 接口定义")
    void testGenerateSnowflakeId_InterfaceDefinition() {
        // Given
        Long expectedId = 1234567890123456789L;
        ApiResponse<Long> mockResponse = ApiResponse.success(expectedId);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(mockResponse);

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("测试 generateBatchSnowflakeIds 接口定义")
    void testGenerateBatchSnowflakeIds_InterfaceDefinition() {
        // Given
        int count = 5;
        List<Long> expectedIds = Arrays.asList(
            1234567890123456789L,
            1234567890123456790L,
            1234567890123456791L,
            1234567890123456792L,
            1234567890123456793L
        );
        ApiResponse<List<Long>> mockResponse = ApiResponse.success(expectedIds);
        when(idGeneratorFeignClient.generateBatchSnowflakeIds(anyInt())).thenReturn(mockResponse);

        // When
        ApiResponse<List<Long>> response = idGeneratorFeignClient.generateBatchSnowflakeIds(count);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(5);
        assertThat(response.getData()).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("测试 generateSegmentId 接口定义")
    void testGenerateSegmentId_InterfaceDefinition() {
        // Given
        String bizTag = "user";
        Long expectedId = 1001L;
        ApiResponse<Long> mockResponse = ApiResponse.success(expectedId);
        when(idGeneratorFeignClient.generateSegmentId(anyString())).thenReturn(mockResponse);

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSegmentId(bizTag);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("测试接口返回错误响应")
    void testInterfaceReturnsErrorResponse() {
        // Given
        ApiResponse<Long> errorResponse = ApiResponse.fail(500, "服务不可用");
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(errorResponse);

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("服务不可用");
    }
}
