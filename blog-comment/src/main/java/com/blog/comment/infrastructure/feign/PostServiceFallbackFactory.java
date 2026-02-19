package com.blog.comment.infrastructure.feign;

import com.blog.api.dto.post.PostDTO;
import com.blog.common.result.ApiResponse;
import com.blog.common.result.ResultCode;
import com.blog.common.sentinel.AbstractFallbackFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文章服务降级工厂
 *
 * @author Blog Team
 */
@Slf4j
@Component
public class PostServiceFallbackFactory extends AbstractFallbackFactory<PostServiceClient> {

    public PostServiceFallbackFactory(MeterRegistry meterRegistry) {
        super("post-service", meterRegistry);
    }

    @Override
    protected PostServiceClient createFallback(Throwable cause) {
        return new PostServiceClient() {
            @Override
            public ApiResponse<PostDTO> getPost(Long postId) {
                log.warn("PostService.getPost fallback triggered, postId={}, cause={}", 
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Boolean> postExists(Long postId) {
                log.warn("PostService.postExists fallback triggered, postId={}, cause={}", 
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<java.util.Map<Long, PostDTO>> batchGetPosts(java.util.Set<Long> postIds) {
                log.warn("PostService.batchGetPosts fallback triggered, postIds={}, cause={}", 
                        postIds, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }
        };
    }
}
