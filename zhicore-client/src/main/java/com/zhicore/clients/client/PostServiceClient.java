package com.zhicore.api.client;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 文章服务 Feign 客户端
 * 注意：fallbackFactory 应在各服务中通过 @FeignClient 配置指定
 */
@FeignClient(name = "zhicore-content")
public interface PostServiceClient extends PostSearchClient {
}
