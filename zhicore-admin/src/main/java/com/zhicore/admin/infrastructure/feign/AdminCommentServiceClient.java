package com.zhicore.admin.infrastructure.feign;

import com.zhicore.api.client.AdminCommentClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 管理服务调用评论服务的 Feign 客户端
 */
@FeignClient(
        name = "zhicore-comment",
        contextId = "adminCommentServiceClient",
        fallbackFactory = AdminCommentServiceFallbackFactory.class
)
public interface AdminCommentServiceClient extends AdminCommentClient {
}
