package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.client.PostCommentClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 文章服务 Feign 客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-content", fallbackFactory = PostServiceFallbackFactory.class)
public interface PostServiceClient extends PostCommentClient {
}
