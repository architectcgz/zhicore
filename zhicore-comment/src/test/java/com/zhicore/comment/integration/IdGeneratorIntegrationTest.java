package com.zhicore.comment.integration;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ID生成服务集成测试
 * 
 * 验证 ZhiCore-comment 服务能够正确使用 IdGeneratorFeignClient
 */
@SpringBootTest
@ActiveProfiles("test")
class IdGeneratorIntegrationTest {

    @MockBean
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Autowired
    private IdGeneratorFeignClient injectedClient;

    @Test
    void shouldInjectIdGeneratorFeignClient() {
        // 验证 IdGeneratorFeignClient 能够被正确注入
        assertThat(injectedClient).isNotNull();
    }

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
        List<Long> expectedIds = Arrays.asList(
                1234567890123456789L,
                1234567890123456790L,
                1234567890123456791L
        );
        when(idGeneratorFeignClient.generateBatchSnowflakeIds(3))
                .thenReturn(ApiResponse.success(expectedIds));

        // When
        ApiResponse<List<Long>> response = idGeneratorFeignClient.generateBatchSnowflakeIds(3);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(3);
        assertThat(response.getData()).containsExactlyElementsOf(expectedIds);
    }

    @Test
    void shouldGenerateSegmentId() {
        // Given
        String bizTag = "comment";
        Long expectedId = 1000001L;
        when(idGeneratorFeignClient.generateSegmentId(bizTag))
                .thenReturn(ApiResponse.success(expectedId));

        // When
        ApiResponse<Long> response = idGeneratorFeignClient.generateSegmentId(bizTag);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(expectedId);
    }
}
