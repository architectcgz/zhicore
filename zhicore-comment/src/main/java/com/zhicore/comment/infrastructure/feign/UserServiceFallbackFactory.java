package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.sentinel.AbstractFallbackFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务降级工厂
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class UserServiceFallbackFactory extends AbstractFallbackFactory<UserServiceClient> {

    public UserServiceFallbackFactory(MeterRegistry meterRegistry) {
        super("user-service", meterRegistry);
    }

    @Override
    protected UserServiceClient createFallback(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public ApiResponse<UserSimpleDTO> getUserSimple(Long userId) {
                log.warn("UserService.getUserSimple fallback triggered, userId={}, cause={}", 
                        userId, cause.getMessage());
                // 返回默认用户信息
                UserSimpleDTO defaultUser = new UserSimpleDTO();
                defaultUser.setId(userId);
                defaultUser.setNickName("用户" + userId);
                defaultUser.setAvatarUrl("");
                return ApiResponse.success(defaultUser);
            }

            @Override
            public ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsers(Set<Long> userIds) {
                log.warn("UserService.batchGetUsers fallback triggered, userIds={}, cause={}", 
                        userIds, cause.getMessage());
                // 返回空映射，让调用方处理
                return ApiResponse.success(Collections.emptyMap());
            }
        };
    }
}
