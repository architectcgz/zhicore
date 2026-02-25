package com.zhicore.comment.infrastructure.feign;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * 文章服务 Feign 客户端
 *
 * @author ZhiCore Team
 */
@FeignClient(name = "zhicore-content", fallbackFactory = PostServiceFallbackFactory.class)
public interface PostServiceClient {

    /**
     * 获取文章详情
     */
    @GetMapping("/api/v1/posts/{postId}")
    ApiResponse<PostDTO> getPost(@PathVariable("postId") Long postId);

    /**
     * 验证文章是否存在
     */
    @GetMapping("/api/v1/posts/{postId}/exists")
    ApiResponse<Boolean> postExists(@PathVariable("postId") Long postId);

    /**
     * 批量获取文章信息
     */
    @PostMapping("/api/v1/posts/batch")
    ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds);
}
