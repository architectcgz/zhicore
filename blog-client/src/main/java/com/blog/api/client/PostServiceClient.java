package com.blog.api.client;

import com.blog.api.dto.post.PostDTO;
import com.blog.api.dto.post.PostDetailDTO;
import com.blog.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 文章服务 Feign 客户端
 * 注意：fallbackFactory 应在各服务中通过 @FeignClient 配置指定
 */
@FeignClient(name = "post-service")
public interface PostServiceClient {

    /**
     * 获取文章详情
     */
    @GetMapping("/posts/{postId}")
    ApiResponse<PostDetailDTO> getPostById(@PathVariable("postId") Long postId);

    /**
     * 获取文章简要信息
     */
    @GetMapping("/posts/{postId}/simple")
    ApiResponse<PostDTO> getPostSimple(@PathVariable("postId") Long postId);

    /**
     * 批量获取文章简要信息
     */
    @GetMapping("/posts/batch/simple")
    ApiResponse<List<PostDTO>> getPostsSimple(@RequestParam("postIds") List<Long> postIds);

    /**
     * 获取文章作者ID
     */
    @GetMapping("/posts/{postId}/author")
    ApiResponse<Long> getPostAuthorId(@PathVariable("postId") Long postId);
}
