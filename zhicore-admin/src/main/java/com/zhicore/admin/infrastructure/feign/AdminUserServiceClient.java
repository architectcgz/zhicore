package com.zhicore.admin.infrastructure.feign;

import com.zhicore.api.client.AdminUserClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 管理服务调用用户服务的 Feign 客户端
 */
@FeignClient(
        name = "zhicore-user",
        contextId = "adminUserServiceClient",
        fallbackFactory = AdminUserServiceFallbackFactory.class
)
public interface AdminUserServiceClient extends AdminUserClient {
}
