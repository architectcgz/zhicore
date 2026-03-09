package com.zhicore.ranking.infrastructure.feign;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * ranking 服务文章客户端降级工厂。
 */
@Slf4j
@Component
public class PostServiceFallbackFactory implements FallbackFactory<PostServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public PostServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-content");
    }

    @Override
    public PostServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new PostServiceClient() {
            @Override
            public ApiResponse<Map<Long, PostDTO>> batchGetPosts(Set<Long> postIds) {
                log.warn("Ranking PostServiceClient.batchGetPosts fallback triggered: postIds={}, cause={}",
                        postIds, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }
        };
    }
}
