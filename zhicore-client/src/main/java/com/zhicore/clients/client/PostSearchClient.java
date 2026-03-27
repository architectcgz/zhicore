package com.zhicore.api.client;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 搜索域所需的文章查询契约。
 */
public interface PostSearchClient extends PostCommentClient {

    @GetMapping("/api/v1/posts/{postId}")
    ApiResponse<PostDetailDTO> getPostById(@PathVariable("postId") Long postId);

    @GetMapping("/api/v1/posts/{postId}/simple")
    ApiResponse<PostDTO> getPostSimple(@PathVariable("postId") Long postId);

    @GetMapping("/api/v1/posts/batch/simple")
    ApiResponse<List<PostDTO>> getPostsSimple(@RequestParam("postIds") List<Long> postIds);

    @GetMapping("/api/v1/posts/{postId}/author")
    ApiResponse<Long> getPostAuthorId(@PathVariable("postId") Long postId);
}
