package com.blog.api.client.fallback;

import com.blog.api.client.IdGeneratorFeignClient;
import com.blog.common.result.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdGeneratorFeignClientFallbackFactory 单元测试
 * 
 * 测试 Fallback 降级逻辑
 */
@DisplayName("ID生成服务 Fallback 降级测试")
class IdGeneratorFeignClientFallbackFactoryTest {

    private IdGeneratorFeignClientFallbackFactory fallbackFactory;
    private IdGeneratorFeignClient fallbackClient;

    @BeforeEach
    void setUp() {
        fallbackFactory = new IdGeneratorFeignClientFallbackFactory();
        // 模拟服务调用失败
        Throwable cause = new RuntimeException("服务连接超时");
        fallbackClient = fallbackFactory.create(cause);
    }

    @Test
    @DisplayName("测试 generateSnowflakeId 降级返回错误响应")
    void testGenerateSnowflakeId_FallbackReturnsError() {
        // When
        ApiResponse<Long> response = fallbackClient.generateSnowflakeId();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("ID生成服务暂时不可用，请稍后重试");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("测试 generateBatchSnowflakeIds 降级返回错误响应")
    void testGenerateBatchSnowflakeIds_FallbackReturnsError() {
        // Given
        int count = 10;

        // When
        ApiResponse<List<Long>> response = fallbackClient.generateBatchSnowflakeIds(count);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("ID生成服务暂时不可用，请稍后重试");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("测试 generateSegmentId 降级返回错误响应")
    void testGenerateSegmentId_FallbackReturnsError() {
        // Given
        String bizTag = "user";

        // When
        ApiResponse<Long> response = fallbackClient.generateSegmentId(bizTag);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("ID生成服务暂时不可用，请稍后重试");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("测试 Fallback 不抛出异常")
    void testFallback_DoesNotThrowException() {
        // Given
        Throwable cause = new RuntimeException("网络异常");
        IdGeneratorFeignClient client = fallbackFactory.create(cause);

        // When & Then - 不应抛出异常
        ApiResponse<Long> response1 = client.generateSnowflakeId();
        assertThat(response1).isNotNull();

        ApiResponse<List<Long>> response2 = client.generateBatchSnowflakeIds(5);
        assertThat(response2).isNotNull();

        ApiResponse<Long> response3 = client.generateSegmentId("test");
        assertThat(response3).isNotNull();
    }

    @Test
    @DisplayName("测试不同异常原因的 Fallback 行为一致")
    void testFallback_ConsistentBehaviorForDifferentExceptions() {
        // Given - 不同类型的异常
        Throwable[] causes = {
            new RuntimeException("连接超时"),
            new IllegalStateException("服务不可用"),
            new NullPointerException("空指针异常")
        };

        for (Throwable cause : causes) {
            // When
            IdGeneratorFeignClient client = fallbackFactory.create(cause);
            ApiResponse<Long> response = client.generateSnowflakeId();

            // Then - 所有异常都返回相同的错误响应
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getMessage()).isEqualTo("ID生成服务暂时不可用，请稍后重试");
        }
    }
}
