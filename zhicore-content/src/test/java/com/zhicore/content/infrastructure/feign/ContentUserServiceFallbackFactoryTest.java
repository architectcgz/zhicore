package com.zhicore.content.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("ContentUserServiceFallbackFactory 测试")
class ContentUserServiceFallbackFactoryTest {

    private final ContentUserServiceFallbackFactory fallbackFactory =
            new ContentUserServiceFallbackFactory(new SimpleMeterRegistry());

    @Test
    @DisplayName("用户服务降级时应该返回统一服务降级错误码")
    void shouldReturnServiceDegradedResponse() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.batchGetUsersSimple(Set.of(1L));

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
        assertEquals("用户服务已降级", response.getMessage());
    }
}
