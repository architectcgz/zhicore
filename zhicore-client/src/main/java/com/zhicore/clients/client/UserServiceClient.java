package com.zhicore.api.client;

import com.zhicore.api.dto.user.UserDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务 Feign 客户端
 * 注意：fallbackFactory 应在各服务中通过 @FeignClient 配置指定
 */
@FeignClient(name = "zhicore-user")
public interface UserServiceClient {

    /**
     * 获取用户详情
     */
    @GetMapping("/users/{userId}")
    ApiResponse<UserDTO> getUserById(@PathVariable("userId") Long userId);

    /**
     * 获取用户简要信息
     */
    @GetMapping("/api/v1/users/{userId}/simple")
    ApiResponse<UserSimpleDTO> getUserSimple(@PathVariable("userId") Long userId);

    /**
     * 批量获取用户简要信息（GET方式）
     */
    @GetMapping("/users/batch/simple")
    ApiResponse<List<UserSimpleDTO>> getUsersSimple(@RequestParam("userIds") List<Long> userIds);

    /**
     * 批量获取用户简要信息（POST方式）
     */
    @PostMapping("/api/v1/users/batch/simple")
    ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsersSimple(@RequestBody Set<Long> userIds);

    /**
     * 批量获取用户信息（管理员用）
     */
    @GetMapping("/users/batch")
    ApiResponse<Map<Long, UserDTO>> batchGetUsers(@RequestParam("userIds") Set<Long> userIds);

    /**
     * 检查是否关注
     */
    @GetMapping("/users/{userId}/following/{targetUserId}")
    ApiResponse<Boolean> isFollowing(
            @PathVariable("userId") Long userId,
            @PathVariable("targetUserId") Long targetUserId);
}
