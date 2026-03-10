package com.zhicore.admin.infrastructure.feign;

import com.zhicore.api.client.AdminPostClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 管理服务调用文章服务的 Feign 客户端
 */
@FeignClient(
        name = "zhicore-content",
        contextId = "adminPostServiceClient",
        fallbackFactory = AdminPostServiceFallbackFactory.class
)
public interface AdminPostServiceClient extends AdminPostClient {
}
