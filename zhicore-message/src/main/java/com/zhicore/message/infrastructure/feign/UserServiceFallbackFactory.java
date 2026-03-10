package com.zhicore.message.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户服务降级工厂
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public UserServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-user");
    }

    @Override
    public UserServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new UserServiceClient() {
            @Override
            public ApiResponse<UserSimpleDTO> getUserSimple(Long userId) {
                log.warn("获取用户简要信息降级: userId={}, cause={}", userId, failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }

            @Override
            public ApiResponse<Boolean> isBlocked(Long userId, Long targetUserId) {
                log.warn("检查用户拉黑状态降级: userId={}, targetUserId={}, cause={}",
                        userId, targetUserId, failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }

            @Override
            public ApiResponse<Boolean> isFollowing(Long userId, Long targetUserId) {
                log.warn("检查关注状态降级: userId={}, targetUserId={}, cause={}",
                        userId, targetUserId, failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }

            @Override
            public ApiResponse<Boolean> isStrangerMessageAllowed(Long userId) {
                log.warn("获取陌生人消息设置降级: userId={}, cause={}", userId, failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }
        };
    }

    private String failureMessage(Throwable cause) {
        return fallbackSupport.failureMessage(cause);
    }
}
