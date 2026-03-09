package com.zhicore.user.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("User UploadServiceFallbackFactory 测试")
class UploadServiceFallbackFactoryTest {

    private final UploadServiceFallbackFactory fallbackFactory =
            new UploadServiceFallbackFactory(new SimpleMeterRegistry());

    @Test
    @DisplayName("获取文件 URL 降级时应该返回服务降级错误")
    void shouldFailWhenGetFileUrlFallbackTriggered() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.getFileUrl("file-id");

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
        assertEquals("上传服务已降级", response.getMessage());
    }
}
