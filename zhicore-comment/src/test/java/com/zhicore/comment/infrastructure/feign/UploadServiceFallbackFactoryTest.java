package com.zhicore.comment.infrastructure.feign;

import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Comment UploadServiceFallbackFactory 测试")
class UploadServiceFallbackFactoryTest {

    private final UploadServiceFallbackFactory fallbackFactory =
            new UploadServiceFallbackFactory(new SimpleMeterRegistry());

    @Test
    @DisplayName("评论图片上传降级时应该返回服务降级错误")
    void shouldFailWhenUploadImageFallbackTriggered() {
        var fallback = fallbackFactory.create(new RuntimeException("timeout"));

        var response = fallback.uploadImage(null);

        assertFalse(response.isSuccess());
        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), response.getCode());
        assertEquals("上传服务已降级", response.getMessage());
    }
}
