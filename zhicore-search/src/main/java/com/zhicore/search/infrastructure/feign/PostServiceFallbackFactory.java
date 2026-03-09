package com.zhicore.search.infrastructure.feign;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PostServiceClient 降级工厂
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
            public ApiResponse<PostDetailDTO> getPostById(Long postId) {
                log.warn("PostServiceClient.getPostById fallback triggered: postId={}, cause={}",
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<PostDTO> getPostSimple(Long postId) {
                log.warn("PostServiceClient.getPostSimple fallback triggered: postId={}, cause={}",
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<List<PostDTO>> getPostsSimple(List<Long> postIds) {
                log.warn("PostServiceClient.getPostsSimple fallback triggered: postIds={}, cause={}",
                        postIds, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<Long> getPostAuthorId(Long postId) {
                log.warn("PostServiceClient.getPostAuthorId fallback triggered: postId={}, cause={}",
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<Boolean> postExists(Long postId) {
                log.warn("PostServiceClient.postExists fallback triggered: postId={}, cause={}",
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<PostDTO> getPost(Long postId) {
                log.warn("PostServiceClient.getPost fallback triggered: postId={}, cause={}",
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }

            @Override
            public ApiResponse<Map<Long, PostDTO>> batchGetPosts(Set<Long> postIds) {
                log.warn("PostServiceClient.batchGetPosts fallback triggered: postIds={}, cause={}",
                        postIds, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }
        };
    }
}
