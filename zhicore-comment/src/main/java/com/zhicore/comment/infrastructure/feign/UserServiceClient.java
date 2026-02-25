package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * 用户服务 Feign 客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-user", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient {

    /**
     * 获取用户简要信息
     */
    @GetMapping("/api/v1/users/{userId}/simple")
    ApiResponse<UserSimpleDTO> getUserSimple(@PathVariable("userId") Long userId);

    /**
     * 批量获取用户简要信息
     */
    @PostMapping("/api/v1/users/batch/simple")
    ApiResponse<Map<Long, UserSimpleDTO>> batchGetUsers(@RequestBody Set<Long> userIds);
}
