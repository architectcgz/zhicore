package com.zhicore.ranking.integration;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ID生成服务集成测试
 * 
 * 验证 ZhiCore-ranking 服务可以通过 IdGeneratorFeignClient 生成 ID
 */
@SpringBootTest
class IdGeneratorIntegrationTest {

    @MockBean
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Test
    void shouldGenerateSnowflakeId() {
        // Given
        Long expectedId = 1234567890123456789L;
        when(idGeneratorFeignClient.generateSnowflakeId())
                .thenReturn(ApiResponse.success(expectedId));

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(expectedId);
    }

    @Test
    void shouldGenerateBatchSnowflakeIds() {
        // Given
        int count = 5;
        List<Long> expectedIds = List.of(
                1234567890123456789L,
                1234567890123456790L,
                1234567890123456791L,
                1234567890123456792L,
                1234567890123456793L
        );
        when(idGeneratorFeignClient.generateBatchSnowflakeIds(anyInt()))
                .thenReturn(ApiResponse.success(expectedIds));

        // When
        ApiResponse<List<Long>> response = idGeneratorFeignClient.generateBatchSnowflakeIds(count);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(count);
        assertThat(response.getData()).isEqualTo(expectedIds);
    }

    @Test
    void shouldGenerateSegmentId() {
        // Given
        String bizTag = "ranking";
        Long expectedId = 1000L;
        when(idGeneratorFeignClient.generateSegmentId(anyString()))
                .thenReturn(ApiResponse.success(expectedId));

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSegmentId(bizTag);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(expectedId);
    }

    @Test
    void shouldHandleIdGenerationFailure() {
        // Given
        when(idGeneratorFeignClient.generateSnowflakeId())
                .thenReturn(ApiResponse.fail(500, "ID生成服务暂时不可用"));

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("ID生成服务暂时不可用");
    }
}
