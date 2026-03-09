package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("AdminFallbackFactory 测试")
class AdminFallbackFactoryTest {

    @Test
    @DisplayName("用户服务降级时应该返回统一服务降级错误码")
    void shouldUseServiceDegradedForAdminUserFallback() {
        var fallback = new AdminUserServiceFallbackFactory(new SimpleMeterRegistry())
                .create(new RuntimeException("timeout"));

        var response = fallback.disableUser(1001L);

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
    }

    @Test
    @DisplayName("ID 服务降级时应该返回统一服务降级错误码")
    void shouldUseServiceDegradedForIdGeneratorFallback() {
        var fallback = new IdGeneratorClientFallbackFactory(new SimpleMeterRegistry())
                .create(new RuntimeException("timeout"));

        var response = fallback.generateSnowflakeId();

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
    }
}
