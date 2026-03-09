package com.zhicore.content.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * content 服务用户客户端降级工厂。
 */
@Slf4j
@Component
public class ContentUserServiceFallbackFactory implements FallbackFactory<ContentUserServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public ContentUserServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-user");
    }

    @Override
    public ContentUserServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return userIds -> {
            log.warn("ContentUserServiceClient.batchGetUsersSimple fallback triggered: userIds={}, cause={}",
                    userIds, fallbackSupport.failureMessage(cause));
            return fallbackSupport.degraded("用户服务已降级");
        };
    }
}
