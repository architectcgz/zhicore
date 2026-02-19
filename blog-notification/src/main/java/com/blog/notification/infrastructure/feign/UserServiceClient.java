package com.blog.notification.infrastructure.feign;

import com.blog.api.dto.user.UserSimpleDTO;
import com.blog.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务 Feign 客户端
 *
 * @author Blog Team
 */
@FeignClient(
        name = "blog-user",
        fallbackFactory = UserServiceFallbackFactory.class
)
public interface UserServiceClient {

    /**
     * 获取用户简要信息
     */
    @GetMapping("/users/{userId}/simple")
    ApiResponse<UserSimpleDTO> getUserSimple(@PathVariable("userId") String userId);

    /**
     * 批量获取用户简要信息
     */
    @GetMapping("/users/batch/simple")
    ApiResponse<List<UserSimpleDTO>> getUsersSimple(@RequestParam("userIds") List<String> userIds);
}
