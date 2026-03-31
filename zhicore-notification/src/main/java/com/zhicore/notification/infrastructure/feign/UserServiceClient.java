package com.zhicore.notification.infrastructure.feign;

import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.client.UserFollowerCursorClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 用户服务 Feign 客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(
        name = "zhicore-user",
        fallbackFactory = UserServiceFallbackFactory.class
)
public interface UserServiceClient extends UserBatchSimpleClient, UserFollowerCursorClient {
}
