package com.zhicore.message.infrastructure.feign;

import com.zhicore.api.client.UserMessagingClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 用户服务Feign客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(
        name = "zhicore-user",
        url = "${ZHICORE_USER_URL:http://zhicore-user:8081}",
        fallbackFactory = UserServiceFallbackFactory.class
)
public interface UserServiceClient extends UserMessagingClient {
}
