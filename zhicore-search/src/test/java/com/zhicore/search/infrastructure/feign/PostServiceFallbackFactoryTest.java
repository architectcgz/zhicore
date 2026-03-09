package com.zhicore.search.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("PostServiceFallbackFactory 测试")
class PostServiceFallbackFactoryTest {

    private final PostServiceFallbackFactory fallbackFactory =
            new PostServiceFallbackFactory(new SimpleMeterRegistry());

    @Test
    @DisplayName("批量获取文章简要信息降级时不应该伪造空成功响应")
    void shouldFailWhenGetPostsSimpleFallbackTriggered() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.getPostsSimple(List.of(1L, 2L));

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_UNAVAILABLE.getCode(), response.getCode());
        assertEquals("文章服务暂时不可用", response.getMessage());
    }

    @Test
    @DisplayName("批量获取文章映射降级时不应该伪造空成功响应")
    void shouldFailWhenBatchGetPostsFallbackTriggered() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.batchGetPosts(Set.of(1L, 2L));

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_UNAVAILABLE.getCode(), response.getCode());
        assertEquals("文章服务暂时不可用", response.getMessage());
    }
}
