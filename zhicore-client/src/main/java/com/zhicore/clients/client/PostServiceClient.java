package com.zhicore.api.client;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章服务 Feign 客户端
 * 注意：fallbackFactory 应在各服务中通过 @FeignClient 配置指定
 */
@FeignClient(name = "zhicore-content")
public interface PostServiceClient {

    /**
     * 获取文章详情
     */
    @GetMapping("/posts/{postId}")
    ApiResponse<PostDetailDTO> getPostById(@PathVariable("postId") Long postId);

    /**
     * 获取文章详情（带完整路径）
     */
    @GetMapping("/api/v1/posts/{postId}")
    ApiResponse<PostDTO> getPost(@PathVariable("postId") Long postId);

    /**
     * 获取文章简要信息
     */
    @GetMapping("/posts/{postId}/simple")
    ApiResponse<PostDTO> getPostSimple(@PathVariable("postId") Long postId);

    /**
     * 批量获取文章简要信息（GET方式）
     */
    @GetMapping("/posts/batch/simple")
    ApiResponse<List<PostDTO>> getPostsSimple(@RequestParam("postIds") List<Long> postIds);

    /**
     * 批量获取文章信息（POST方式）
     */
    @PostMapping("/api/v1/posts/batch")
    ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds);

    /**
     * 获取文章作者ID
     */
    @GetMapping("/posts/{postId}/author")
    ApiResponse<Long> getPostAuthorId(@PathVariable("postId") Long postId);

    /**
     * 验证文章是否存在
     */
    @GetMapping("/api/v1/posts/{postId}/exists")
    ApiResponse<Boolean> postExists(@PathVariable("postId") Long postId);
}
