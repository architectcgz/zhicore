package com.zhicore.ranking.infrastructure.feign;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * ranking 服务专用文章客户端。
 *
 * <p>对外部依赖的降级语义在本服务内统一定义，避免共享客户端把失败静默成空数据。
 */
@FeignClient(name = "zhicore-content", fallbackFactory = PostServiceFallbackFactory.class)
public interface PostServiceClient {

    @PostMapping("/api/v1/posts/batch")
    ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds);
}
