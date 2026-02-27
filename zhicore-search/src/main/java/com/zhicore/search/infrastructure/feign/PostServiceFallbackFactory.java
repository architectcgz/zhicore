package com.zhicore.search.infrastructure.feign;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.sentinel.AbstractFallbackFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
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
public class PostServiceFallbackFactory extends AbstractFallbackFactory<PostServiceClient> {

    public PostServiceFallbackFactory(MeterRegistry meterRegistry) {
        super("post-service", meterRegistry);
    }

    @Override
    protected PostServiceClient createFallback(Throwable cause) {
        return new PostServiceClient() {
            @Override
            public ApiResponse<PostDetailDTO> getPostById(Long postId) {
                log.warn("PostServiceClient.getPostById fallback triggered: postId={}, cause={}",
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<PostDTO> getPostSimple(Long postId) {
                log.warn("PostServiceClient.getPostSimple fallback triggered: postId={}, cause={}",
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<List<PostDTO>> getPostsSimple(List<Long> postIds) {
                log.warn("PostServiceClient.getPostsSimple fallback triggered: postIds={}, cause={}",
                        postIds, cause.getMessage());
                return ApiResponse.success(Collections.emptyList());
            }

            @Override
            public ApiResponse<Long> getPostAuthorId(Long postId) {
                log.warn("PostServiceClient.getPostAuthorId fallback triggered: postId={}, cause={}",
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Boolean> postExists(Long postId) {
                log.warn("PostServiceClient.postExists fallback triggered: postId={}, cause={}",
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<PostDTO> getPost(Long postId) {
                log.warn("PostServiceClient.getPost fallback triggered: postId={}, cause={}",
                        postId, cause.getMessage());
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Map<Long, PostDTO>> batchGetPosts(Set<Long> postIds) {
                log.warn("PostServiceClient.batchGetPosts fallback triggered: postIds={}, cause={}",
                        postIds, cause.getMessage());
                return ApiResponse.success(Collections.emptyMap());
            }
        };
    }
}
