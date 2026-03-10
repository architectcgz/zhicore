package com.zhicore.api.client;

import com.zhicore.api.dto.user.UserDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端
 * 注意：fallbackFactory 应在各服务中通过 @FeignClient 配置指定
 */
@FeignClient(name = "zhicore-user")
public interface UserServiceClient extends UserSimpleBatchClient {

    /**
     * 获取用户详情
     */
    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserDTO> getUserById(@PathVariable("userId") Long userId);

    @GetMapping("/api/v1/users/{blockerId}/blocking/{blockedId}/check")
    ApiResponse<Boolean> isBlocked(@PathVariable("blockerId") Long blockerId,
                                   @PathVariable("blockedId") Long blockedId);

    @GetMapping("/api/v1/users/{userId}/following/{targetUserId}/check")
    ApiResponse<Boolean> isFollowing(@PathVariable("userId") Long userId,
                                     @PathVariable("targetUserId") Long targetUserId);

    @GetMapping("/api/v1/users/{userId}/settings/stranger-message")
    ApiResponse<Boolean> isStrangerMessageAllowed(@PathVariable("userId") Long userId);
}
