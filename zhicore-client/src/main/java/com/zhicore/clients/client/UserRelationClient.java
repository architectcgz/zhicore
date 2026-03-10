package com.zhicore.api.client;

import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户关系查询契约。
 */
public interface UserRelationClient {

    @GetMapping("/api/v1/users/{blockerId}/blocking/{blockedId}/check")
    ApiResponse<Boolean> isBlocked(@PathVariable("blockerId") Long blockerId,
                                   @PathVariable("blockedId") Long blockedId);

    @GetMapping("/api/v1/users/{userId}/following/{targetUserId}/check")
    ApiResponse<Boolean> isFollowing(@PathVariable("userId") Long userId,
                                     @PathVariable("targetUserId") Long targetUserId);

    @GetMapping("/api/v1/users/{userId}/settings/stranger-message")
    ApiResponse<Boolean> isStrangerMessageAllowed(@PathVariable("userId") Long userId);
}
