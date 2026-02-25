package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 管理服务调用用户服务的降级工厂
 */
@Slf4j
@Component
public class AdminUserServiceFallbackFactory implements FallbackFactory<AdminUserServiceClient> {
    
    @Override
    public AdminUserServiceClient create(Throwable cause) {
        log.error("Admin user service fallback triggered", cause);
        return new AdminUserServiceClient() {
            @Override
            public ApiResponse<PageResult<UserManageDTO>> queryUsers(String keyword, String status, int page, int size) {
                log.error("Fallback: queryUsers() - User service unavailable");
                return ApiResponse.fail("用户服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Void> disableUser(Long userId) {
                log.error("Fallback: disableUser({}) - User service unavailable", userId);
                return ApiResponse.fail("用户服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Void> enableUser(Long userId) {
                log.error("Fallback: enableUser({}) - User service unavailable", userId);
                return ApiResponse.fail("用户服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Void> invalidateUserTokens(Long userId) {
                log.error("Fallback: invalidateUserTokens({}) - User service unavailable", userId);
                return ApiResponse.fail("用户服务暂时不可用");
            }
        };
    }
}
