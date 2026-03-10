package com.zhicore.api.client;

import com.zhicore.api.dto.admin.UserManageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理侧用户服务契约。
 */
public interface AdminUserClient {

    @GetMapping("/api/v1/admin/users")
    ApiResponse<PageResult<UserManageDTO>> queryUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @PostMapping("/api/v1/admin/users/{userId}/disable")
    ApiResponse<Void> disableUser(@PathVariable("userId") Long userId);

    @PostMapping("/api/v1/admin/users/{userId}/enable")
    ApiResponse<Void> enableUser(@PathVariable("userId") Long userId);

    @PostMapping("/api/v1/admin/users/{userId}/invalidate-tokens")
    ApiResponse<Void> invalidateUserTokens(@PathVariable("userId") Long userId);
}
