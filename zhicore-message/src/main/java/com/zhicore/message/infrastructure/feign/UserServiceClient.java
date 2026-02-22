package com.zhicore.message.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务Feign客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-user", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient {

    /**
     * 获取用户简要信息
     *
     * @param userId 用户ID
     * @return 用户简要信息
     */
    @GetMapping("/api/v1/users/{userId}/simple")
    ApiResponse<UserSimpleDTO> getUserSimple(@PathVariable("userId") String userId);

    /**
     * 检查用户是否被拉黑
     *
     * @param userId 用户ID
     * @param targetUserId 目标用户ID
     * @return 是否被拉黑
     */
    @GetMapping("/api/v1/users/{userId}/blocks/check")
    ApiResponse<Boolean> isBlocked(@PathVariable("userId") String userId,
                                   @RequestParam("targetUserId") String targetUserId);

    /**
     * 检查是否是陌生人（未互相关注）
     *
     * @param userId 用户ID
     * @param targetUserId 目标用户ID
     * @return 是否是陌生人
     */
    @GetMapping("/api/v1/users/{userId}/follows/is-stranger")
    ApiResponse<Boolean> isStranger(@PathVariable("userId") String userId,
                                    @RequestParam("targetUserId") String targetUserId);

    /**
     * 获取用户是否允许陌生人消息
     *
     * @param userId 用户ID
     * @return 是否允许陌生人消息
     */
    @GetMapping("/api/v1/users/{userId}/settings/stranger-message")
    ApiResponse<Boolean> isStrangerMessageAllowed(@PathVariable("userId") String userId);
}
