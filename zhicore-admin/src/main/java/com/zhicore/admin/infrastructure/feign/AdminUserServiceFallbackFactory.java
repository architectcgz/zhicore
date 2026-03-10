package com.zhicore.admin.infrastructure.feign;

import com.zhicore.api.dto.admin.UserManageDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 管理服务调用用户服务的降级工厂
 */
@Slf4j
@Component
public class AdminUserServiceFallbackFactory implements FallbackFactory<AdminUserServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public AdminUserServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-user");
    }

    @Override
    public AdminUserServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new AdminUserServiceClient() {
            @Override
            public ApiResponse<PageResult<UserManageDTO>> queryUsers(String keyword, String status, int page, int size) {
                log.warn("AdminUserServiceClient.queryUsers fallback triggered: keyword={}, status={}, page={}, size={}, cause={}",
                        keyword, status, page, size, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }
            
            @Override
            public ApiResponse<Void> disableUser(Long userId) {
                log.warn("AdminUserServiceClient.disableUser fallback triggered: userId={}, cause={}",
                        userId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }
            
            @Override
            public ApiResponse<Void> enableUser(Long userId) {
                log.warn("AdminUserServiceClient.enableUser fallback triggered: userId={}, cause={}",
                        userId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }
            
            @Override
            public ApiResponse<Void> invalidateUserTokens(Long userId) {
                log.warn("AdminUserServiceClient.invalidateUserTokens fallback triggered: userId={}, cause={}",
                        userId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("用户服务已降级");
            }
        };
    }
}
