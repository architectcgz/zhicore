package com.zhicore.ranking.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Ranking PostServiceFallbackFactory 测试")
class PostServiceFallbackFactoryTest {

    private final PostServiceFallbackFactory fallbackFactory =
            new PostServiceFallbackFactory(new SimpleMeterRegistry());

    @Test
    @DisplayName("批量获取文章信息降级时应该返回服务降级错误")
    void shouldFailWhenBatchGetPostsFallbackTriggered() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.batchGetPosts(Set.of(1L, 2L));

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
        assertEquals("文章服务已降级", response.getMessage());
    }
}
