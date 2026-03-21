package com.zhicore.admin.infrastructure.feign;

import com.zhicore.api.client.IdGeneratorFeignClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * ID生成服务 Feign 客户端
 * 
 * 调用 zhicore-id-generator 服务生成分布式ID
 */
@FeignClient(
        name = "zhicore-id-generator",
        url = "${zhicore.id-generator.server-url:${ID_GENERATOR_SERVER_URL:http://zhicore-id-generator:8088}}",
        contextId = "idGeneratorClient",
        path = "/api/v1/id",
        fallbackFactory = IdGeneratorClientFallbackFactory.class
)
public interface IdGeneratorClient extends IdGeneratorFeignClient {
}
