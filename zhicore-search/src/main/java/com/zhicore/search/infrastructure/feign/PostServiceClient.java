package com.zhicore.search.infrastructure.feign;

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
 * search 服务专用文章客户端。
 *
 * <p>对内容服务的降级策略在搜索服务本地定义，
 * 避免直接注册共享客户端后丢失本地 fallback。
 */
@FeignClient(name = "zhicore-content", fallbackFactory = PostServiceFallbackFactory.class)
public interface PostServiceClient {

    @GetMapping("/posts/{postId}")
    ApiResponse<PostDetailDTO> getPostById(@PathVariable("postId") Long postId);

    @GetMapping("/api/v1/posts/{postId}")
    ApiResponse<PostDTO> getPost(@PathVariable("postId") Long postId);

    @GetMapping("/posts/{postId}/simple")
    ApiResponse<PostDTO> getPostSimple(@PathVariable("postId") Long postId);

    @GetMapping("/posts/batch/simple")
    ApiResponse<List<PostDTO>> getPostsSimple(@RequestParam("postIds") List<Long> postIds);

    @PostMapping("/api/v1/posts/batch")
    ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds);

    @GetMapping("/posts/{postId}/author")
    ApiResponse<Long> getPostAuthorId(@PathVariable("postId") Long postId);

    @GetMapping("/api/v1/posts/{postId}/exists")
    ApiResponse<Boolean> postExists(@PathVariable("postId") Long postId);
}
