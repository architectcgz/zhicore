package com.zhicore.admin.infrastructure.feign;

import com.zhicore.api.dto.admin.CommentManageDTO;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 管理服务调用评论服务的降级工厂
 */
@Slf4j
@Component
public class AdminCommentServiceFallbackFactory implements FallbackFactory<AdminCommentServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public AdminCommentServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-comment");
    }

    @Override
    public AdminCommentServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new AdminCommentServiceClient() {
            @Override
            public ApiResponse<PageResult<CommentManageDTO>> queryComments(String keyword, Long postId, Long userId, int page, int size) {
                log.warn("AdminCommentServiceClient.queryComments fallback triggered: keyword={}, postId={}, userId={}, page={}, size={}, cause={}",
                        keyword, postId, userId, page, size, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("评论服务已降级");
            }
            
            @Override
            public ApiResponse<Void> deleteComment(Long commentId) {
                log.warn("AdminCommentServiceClient.deleteComment fallback triggered: commentId={}, cause={}",
                        commentId, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("评论服务已降级");
            }
        };
    }
}
