package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 管理服务调用用户服务的 Feign 客户端
 */
@FeignClient(
        name = "zhicore-user",
        contextId = "adminUserServiceClient",
        fallbackFactory = AdminUserServiceFallbackFactory.class
)
public interface AdminUserServiceClient {
    
    /**
     * 管理员查询用户列表
     */
    @GetMapping("/api/v1/admin/users")
    ApiResponse<PageResult<UserManageDTO>> queryUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);
    
    /**
     * 禁用用户
     */
    @PostMapping("/api/v1/admin/users/{userId}/disable")
    ApiResponse<Void> disableUser(@PathVariable("userId") Long userId);
    
    /**
     * 启用用户
     */
    @PostMapping("/api/v1/admin/users/{userId}/enable")
    ApiResponse<Void> enableUser(@PathVariable("userId") Long userId);
    
    /**
     * 使用户所有 Token 失效
     */
    @PostMapping("/api/v1/admin/users/{userId}/invalidate-tokens")
    ApiResponse<Void> invalidateUserTokens(@PathVariable("userId") Long userId);
    
    /**
     * 用户管理 DTO
     */
    record UserManageDTO(
            Long id,
            String username,
            String email,
            String nickname,
            String avatar,
            String status,
            java.time.LocalDateTime createdAt,
            java.util.List<String> roles
    ) {}
}
