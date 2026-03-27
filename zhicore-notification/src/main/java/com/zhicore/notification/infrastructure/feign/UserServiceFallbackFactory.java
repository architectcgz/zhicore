package com.zhicore.notification.infrastructure.feign;

import com.zhicore.api.dto.user.FollowerCursorPageDTO;
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
            public ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsersSimple(Set<Long> userIds) {
                log.warn("批量获取用户简要信息降级: userIds={}, cause={}", userIds, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }

            @Override
            public ApiResponse<FollowerCursorPageDTO> getFollowersByCursor(Long userId,
                                                                           java.time.LocalDateTime afterCreatedAt,
                                                                           Long afterFollowerId,
                                                                           Integer limit) {
                log.warn("按游标获取粉丝列表降级: userId={}, cause={}", userId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }
        };
    }
}
