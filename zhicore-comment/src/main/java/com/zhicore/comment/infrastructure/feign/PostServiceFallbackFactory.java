package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.Counter;
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
            public ApiResponse<PostDTO> getPost(Long postId) {
                log.warn("PostService.getPost fallback triggered, postId={}, cause={}", 
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<Boolean> postExists(Long postId) {
                log.warn("PostService.postExists fallback triggered, postId={}, cause={}", 
                        postId, failureMessage(cause));
                return ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用");
            }

            @Override
            public ApiResponse<java.util.Map<Long, PostDTO>> batchGetPosts(java.util.Set<Long> postIds) {
                log.warn("PostService.batchGetPosts fallback triggered, postIds={}, cause={}", 
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
