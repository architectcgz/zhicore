package com.zhicore.search.infrastructure.feign;

import com.zhicore.api.client.PostSearchClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * search 服务专用文章客户端。
 *
 * <p>对内容服务的降级策略在搜索服务本地定义，
 * 避免直接注册共享客户端后丢失本地 fallback。
 */
@FeignClient(name = "zhicore-content", fallbackFactory = PostServiceFallbackFactory.class)
public interface PostServiceClient extends PostSearchClient {
}
