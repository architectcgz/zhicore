package com.zhicore.user.infrastructure.feign;

import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户模块上传服务降级工厂。
 */
@Slf4j
@Component
public class UploadServiceFallbackFactory implements FallbackFactory<ZhiCoreUploadClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public UploadServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-upload");
    }

    @Override
    public ZhiCoreUploadClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new ZhiCoreUploadClient() {
            @Override
            public ApiResponse<String> getFileUrl(String fileId) {
                log.warn("User getFileUrl fallback triggered: fileId={}, cause={}", fileId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("上传服务已降级");
            }

            @Override
            public ApiResponse<Void> deleteFile(String fileId) {
                log.warn("User deleteFile fallback triggered: fileId={}, cause={}", fileId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("上传服务已降级");
            }
        };
    }
}
