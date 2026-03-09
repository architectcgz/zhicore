package com.zhicore.content.infrastructure.feign;

import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * content 服务上传客户端降级工厂。
 */
@Slf4j
@Component
public class ContentUploadServiceFallbackFactory implements FallbackFactory<ContentUploadServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public ContentUploadServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-upload");
    }

    @Override
    public ContentUploadServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new ContentUploadServiceClient() {
            @Override
            public ApiResponse<String> getFileUrl(String fileId) {
                log.warn("ContentUploadServiceClient.getFileUrl fallback triggered: fileId={}, cause={}",
                        fileId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("上传服务已降级");
            }

            @Override
            public ApiResponse<Void> deleteFile(String fileId) {
                log.warn("ContentUploadServiceClient.deleteFile fallback triggered: fileId={}, cause={}",
                        fileId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("上传服务已降级");
            }
        };
    }
}
