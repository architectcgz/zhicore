package com.zhicore.notification.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 用户服务降级工厂
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {

    private static final String SERVICE_NAME = "zhicore-user";

    private final Counter fallbackCounter;

    public UserServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackCounter = meterRegistry.counter("feign.fallback.count", "service", SERVICE_NAME);
    }

    @Override
    public UserServiceClient create(Throwable cause) {
        fallbackCounter.increment();
        logFallback(cause);
        return new UserServiceClient() {
            @Override
            public ApiResponse<UserSimpleDTO> getUserSimple(String userId) {
                log.warn("获取用户简要信息降级: userId={}, cause={}", userId, failureMessage(cause));
                return ApiResponse.fail(com.zhicore.common.result.ResultCode.SERVICE_UNAVAILABLE, "用户服务暂时不可用");
            }

            @Override
            public ApiResponse<List<UserSimpleDTO>> getUsersSimple(List<String> userIds) {
                log.warn("批量获取用户简要信息降级: userIds={}, cause={}", userIds, failureMessage(cause));
                return ApiResponse.fail(com.zhicore.common.result.ResultCode.SERVICE_UNAVAILABLE, "用户服务暂时不可用");
            }
        };
    }

    private void logFallback(Throwable cause) {
        String message = cause != null ? cause.getMessage() : null;
        if (message != null && (message.contains("timeout") || message.contains("Timeout") || message.contains("timed out"))) {
            log.warn("{} 调用超时: {}", SERVICE_NAME, message);
            return;
        }
        log.error("{} 调用失败", SERVICE_NAME, cause);
    }

    private String failureMessage(Throwable cause) {
        return cause != null ? cause.getMessage() : "unknown";
    }
}
