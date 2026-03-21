package com.zhicore.comment.infrastructure.feign;

import com.zhicore.comment.application.dto.RankingHotPostCandidatesResponse;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ranking 服务 Feign 客户端。
 */
@FeignClient(name = "zhicore-ranking", path = "/api/v1/ranking", fallbackFactory = RankingServiceFallbackFactory.class)
public interface RankingServiceClient {

    @GetMapping("/posts/hot/candidates")
    ApiResponse<RankingHotPostCandidatesResponse> getHotPostCandidates();
}
