package com.blog.admin.infrastructure.feign;

import com.blog.common.result.ApiResponse;
import com.blog.common.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 管理服务调用评论服务的降级工厂
 */
@Slf4j
@Component
public class AdminCommentServiceFallbackFactory implements FallbackFactory<AdminCommentServiceClient> {
    
    @Override
    public AdminCommentServiceClient create(Throwable cause) {
        log.error("Admin comment service fallback triggered", cause);
        return new AdminCommentServiceClient() {
            @Override
            public ApiResponse<PageResult<CommentManageDTO>> queryComments(String keyword, Long postId, Long userId, int page, int size) {
                log.error("Fallback: queryComments() - Comment service unavailable");
                return ApiResponse.fail("评论服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Void> deleteComment(Long commentId) {
                log.error("Fallback: deleteComment({}) - Comment service unavailable", commentId);
                return ApiResponse.fail("评论服务暂时不可用");
            }
        };
    }
}
