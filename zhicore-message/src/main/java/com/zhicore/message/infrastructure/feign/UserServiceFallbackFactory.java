package com.zhicore.message.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.sentinel.AbstractFallbackFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务降级工厂
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class UserServiceFallbackFactory extends AbstractFallbackFactory<UserServiceClient> {

    public UserServiceFallbackFactory(MeterRegistry meterRegistry) {
        super("ZhiCore-user", meterRegistry);
    }

    @Override
    protected UserServiceClient createFallback(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public ApiResponse<UserSimpleDTO> getUserSimple(String userId) {
                log.warn("获取用户简要信息降级: userId={}, cause={}", userId, cause.getMessage());
                return serviceDegraded();
            }

            @Override
            public ApiResponse<Boolean> isBlocked(String userId, String targetUserId) {
                log.warn("检查用户拉黑状态降级: userId={}, targetUserId={}, cause={}", 
                        userId, targetUserId, cause.getMessage());
                // 降级时默认不拉黑，允许发送消息
                return ApiResponse.success(false);
            }

            @Override
            public ApiResponse<Boolean> isStranger(String userId, String targetUserId) {
                log.warn("检查陌生人状态降级: userId={}, targetUserId={}, cause={}", 
                        userId, targetUserId, cause.getMessage());
                // 降级时默认不是陌生人，允许发送消息
                return ApiResponse.success(false);
            }

            @Override
            public ApiResponse<Boolean> isStrangerMessageAllowed(String userId) {
                log.warn("获取陌生人消息设置降级: userId={}, cause={}", userId, cause.getMessage());
                // 降级时默认允许陌生人消息
                return ApiResponse.success(true);
            }
        };
    }
}
