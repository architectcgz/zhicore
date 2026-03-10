package com.zhicore.content.infrastructure.feign;

import com.zhicore.api.client.UserBatchSimpleClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * content 服务专用用户客户端。
 */
@FeignClient(name = "zhicore-user", fallbackFactory = ContentUserServiceFallbackFactory.class)
public interface ContentUserServiceClient extends UserBatchSimpleClient {
}
