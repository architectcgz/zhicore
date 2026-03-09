package com.zhicore.content.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("ContentUploadServiceFallbackFactory 测试")
class ContentUploadServiceFallbackFactoryTest {

    private final ContentUploadServiceFallbackFactory fallbackFactory =
            new ContentUploadServiceFallbackFactory(new SimpleMeterRegistry());

    @Test
    @DisplayName("上传服务降级时应该返回统一服务降级错误码")
    void shouldReturnServiceDegradedResponse() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.getFileUrl("file-id");

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
        assertEquals("上传服务已降级", response.getMessage());
    }
}
