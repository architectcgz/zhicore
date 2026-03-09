package com.zhicore.search.infrastructure.feign;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.Counter;
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

    private static final String SERVICE_NAME = "zhicore-content";

    private final Counter fallbackCounter;

    public PostServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackCounter = meterRegistry.counter("feign.fallback.count", "service", SERVICE_NAME);
    }

    @Override
    public PostServiceClient create(Throwable cause) {
        fallbackCounter.increment();
        logFallback(cause);
        return new PostServiceClient() {
            @Override
            public ApiResponse<PostDetailDTO> getPostById(Long postId) {
                log.warn("PostServiceClient.getPostById fallback triggered: postId={}, cause={}",
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<PostDTO> getPostSimple(Long postId) {
                log.warn("PostServiceClient.getPostSimple fallback triggered: postId={}, cause={}",
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<List<PostDTO>> getPostsSimple(List<Long> postIds) {
                log.warn("PostServiceClient.getPostsSimple fallback triggered: postIds={}, cause={}",
                        postIds, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Long> getPostAuthorId(Long postId) {
                log.warn("PostServiceClient.getPostAuthorId fallback triggered: postId={}, cause={}",
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Boolean> postExists(Long postId) {
                log.warn("PostServiceClient.postExists fallback triggered: postId={}, cause={}",
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<PostDTO> getPost(Long postId) {
                log.warn("PostServiceClient.getPost fallback triggered: postId={}, cause={}",
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Map<Long, PostDTO>> batchGetPosts(Set<Long> postIds) {
                log.warn("PostServiceClient.batchGetPosts fallback triggered: postIds={}, cause={}",
                        postIds, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }
        };
    }

    private void logFallback(Throwable cause) {
        String message = cause != null ? cause.getMessage() : null;
        if (message != null && (message.contains("timeout") || message.contains("Timeout") || message.contains("timed out"))) {
            log.warn("{} 调用超时: {}", SERVICE_NAME, message);
            return;
        }
        log.error("{} 调用失败", SERVICE_NAME, cause);
    }

    private String failureMessage(Throwable cause) {
        return cause != null ? cause.getMessage() : "unknown";
    }
}
