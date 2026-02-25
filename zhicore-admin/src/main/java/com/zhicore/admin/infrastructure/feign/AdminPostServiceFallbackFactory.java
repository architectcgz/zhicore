package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 管理服务调用文章服务的降级工厂
 */
@Slf4j
@Component
public class AdminPostServiceFallbackFactory implements FallbackFactory<AdminPostServiceClient> {
    
    @Override
    public AdminPostServiceClient create(Throwable cause) {
        log.error("Admin post service fallback triggered", cause);
        return new AdminPostServiceClient() {
            @Override
            public ApiResponse<PageResult<PostManageDTO>> queryPosts(String keyword, String status, Long authorId, int page, int size) {
                log.error("Fallback: queryPosts() - Post service unavailable");
                return ApiResponse.fail("文章服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Void> deletePost(Long postId) {
                log.error("Fallback: deletePost({}) - Post service unavailable", postId);
                return ApiResponse.fail("文章服务暂时不可用");
            }
        };
    }
}
