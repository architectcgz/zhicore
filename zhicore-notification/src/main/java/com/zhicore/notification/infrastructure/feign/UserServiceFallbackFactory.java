package com.zhicore.notification.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.sentinel.AbstractFallbackFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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
                return ApiResponse.success(createDefaultUser(userId));
            }

            @Override
            public ApiResponse<List<UserSimpleDTO>> getUsersSimple(List<String> userIds) {
                log.warn("批量获取用户简要信息降级: userIds={}, cause={}", userIds, cause.getMessage());
                return ApiResponse.success(Collections.emptyList());
            }
        };
    }

    private UserSimpleDTO createDefaultUser(String userId) {
        UserSimpleDTO user = new UserSimpleDTO();
        user.setId(Long.valueOf(userId));
        user.setNickName("用户");
        user.setUserName("user");
        return user;
    }
}
