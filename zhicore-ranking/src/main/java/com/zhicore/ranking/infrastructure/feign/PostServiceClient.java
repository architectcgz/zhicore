package com.zhicore.ranking.infrastructure.feign;

import com.zhicore.api.client.PostBatchClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * ranking 服务专用文章客户端。
 *
 * <p>对外部依赖的降级语义在本服务内统一定义，避免共享客户端把失败静默成空数据。
 */
@FeignClient(name = "zhicore-content", fallbackFactory = PostServiceFallbackFactory.class)
public interface PostServiceClient extends PostBatchClient {
}
