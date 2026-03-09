package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 管理服务调用文章服务的降级工厂
 */
@Slf4j
@Component
public class AdminPostServiceFallbackFactory implements FallbackFactory<AdminPostServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public AdminPostServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-content");
    }

    @Override
    public AdminPostServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new AdminPostServiceClient() {
            @Override
            public ApiResponse<PageResult<PostManageDTO>> queryPosts(String keyword, String status, Long authorId, int page, int size) {
                log.warn("AdminPostServiceClient.queryPosts fallback triggered: keyword={}, status={}, authorId={}, page={}, size={}, cause={}",
                        keyword, status, authorId, page, size, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }
            
            @Override
            public ApiResponse<Void> deletePost(Long postId) {
                log.warn("AdminPostServiceClient.deletePost fallback triggered: postId={}, cause={}",
                        postId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("文章服务已降级");
            }
        };
    }
}
