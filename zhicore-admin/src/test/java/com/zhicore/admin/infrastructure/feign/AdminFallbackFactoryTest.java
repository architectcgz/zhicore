package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("AdminFallbackFactory 测试")
class AdminFallbackFactoryTest {

    @Test
    @DisplayName("用户服务降级时应该返回统一服务不可用错误码")
    void shouldUseServiceUnavailableForAdminUserFallback() {
        var fallback = new AdminUserServiceFallbackFactory().create(new RuntimeException("timeout"));

        var response = fallback.disableUser(1001L);

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_UNAVAILABLE.getCode(), response.getCode());
    }

    @Test
    @DisplayName("ID 服务降级时应该返回统一服务降级错误码")
    void shouldUseServiceDegradedForIdGeneratorFallback() {
        var fallback = new IdGeneratorClientFallbackFactory().create(new RuntimeException("timeout"));

        var response = fallback.generateSnowflakeId();

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
    }
}
