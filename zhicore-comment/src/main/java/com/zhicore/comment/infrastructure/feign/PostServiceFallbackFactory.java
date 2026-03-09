package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 文章服务降级工厂
 *
 * @author ZhiCore Team
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
            public ApiResponse<PostDTO> getPost(Long postId) {
                log.warn("PostService.getPost fallback triggered, postId={}, cause={}", 
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<Boolean> postExists(Long postId) {
                log.warn("PostService.postExists fallback triggered, postId={}, cause={}", 
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<java.util.Map<Long, PostDTO>> batchGetPosts(java.util.Set<Long> postIds) {
                log.warn("PostService.batchGetPosts fallback triggered, postIds={}, cause={}", 
                        postIds, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }
        };
    }
}
