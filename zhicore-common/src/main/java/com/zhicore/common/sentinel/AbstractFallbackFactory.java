package com.zhicore.common.sentinel;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * Feign 降级工厂基类
 * 
 * 提供通用的降级处理逻辑和监控指标
 *
 * @param <T> Feign 客户端类型
 * @author ZhiCore Team
 */
@Slf4j
public abstract class AbstractFallbackFactory<T> implements FallbackFactory<T> {

    private final String serviceName;
    private final Counter fallbackCounter;

    protected AbstractFallbackFactory(String serviceName, MeterRegistry meterRegistry) {
        this.serviceName = serviceName;
        this.fallbackCounter = meterRegistry.counter("feign.fallback.count", 
                "service", serviceName);
    }

    @Override
    public T create(Throwable cause) {
        // 记录降级指标
        fallbackCounter.increment();
        
        // 记录日志
        logFallback(cause);
        
        // 创建降级实现
        return createFallback(cause);
    }

    /**
     * 创建降级实现
     *
     * @param cause 触发降级的异常
     * @return 降级实现
     */
    protected abstract T createFallback(Throwable cause);

    /**
     * 记录降级日志
     *
     * @param cause 触发降级的异常
     */
    protected void logFallback(Throwable cause) {
        if (isCircuitBreakerOpen(cause)) {
            log.warn("{}服务熔断器已打开: {}", serviceName, cause.getMessage());
        } else if (isTimeout(cause)) {
            log.warn("{}服务调用超时: {}", serviceName, cause.getMessage());
        } else {
            log.error("{}服务调用失败", serviceName, cause);
        }
    }

    /**
     * 判断是否是熔断器打开导致的降级
     */
    protected boolean isCircuitBreakerOpen(Throwable cause) {
        String message = cause.getMessage();
        return message != null && (
                message.contains("CircuitBreaker") || 
                message.contains("circuit breaker") ||
                message.contains("DegradeException"));
    }

    /**
     * 判断是否是超时导致的降级
     */
    protected boolean isTimeout(Throwable cause) {
        String message = cause.getMessage();
        return message != null && (
                message.contains("timeout") || 
                message.contains("Timeout") ||
                message.contains("timed out"));
    }

    /**
     * 创建服务不可用响应
     */
    protected <R> ApiResponse<R> serviceUnavailable() {
        return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, 
                serviceName + "服务暂不可用");
    }

    /**
     * 创建服务降级响应
     */
    protected <R> ApiResponse<R> serviceDegraded() {
        return ApiResponse.fail(ResultCode.SERVICE_DEGRADED, 
                serviceName + "服务已降级");
    }

    /**
     * 获取服务名称
     */
    protected String getServiceName() {
        return serviceName;
    }
}
