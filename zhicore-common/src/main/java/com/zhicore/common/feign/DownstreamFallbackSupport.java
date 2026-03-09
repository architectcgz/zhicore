package com.zhicore.common.feign;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;

/**
 * Feign 降级公共支持类。
 *
 * <p>只负责观测与统一错误码，不包含任何业务返回结构，避免把业务 fallback 逻辑放进共享模块。</p>
 */
public class DownstreamFallbackSupport {

    private final String serviceName;
    private final Counter fallbackCounter;

    public DownstreamFallbackSupport(MeterRegistry meterRegistry, String serviceName) {
        this.serviceName = serviceName;
        this.fallbackCounter = meterRegistry.counter("feign.fallback.count", "service", serviceName);
    }

    public DownstreamFallbackSupport(String serviceName) {
        this.serviceName = serviceName;
        this.fallbackCounter = null;
    }

    public void onFallbackTriggered(Logger log, Throwable cause) {
        if (fallbackCounter != null) {
            fallbackCounter.increment();
        }
        String message = failureMessage(cause);
        if (isTimeout(message)) {
            log.warn("{} 调用超时: {}", serviceName, message);
            return;
        }
        log.error("{} 调用失败", serviceName, cause);
    }

    public String failureMessage(Throwable cause) {
        return cause != null && cause.getMessage() != null ? cause.getMessage() : "unknown";
    }

    public <T> ApiResponse<T> degraded(String message) {
        return ApiResponse.fail(ResultCode.SERVICE_DEGRADED, message);
    }

    private boolean isTimeout(String message) {
        return message.contains("timeout")
                || message.contains("Timeout")
                || message.contains("timed out");
    }
}
