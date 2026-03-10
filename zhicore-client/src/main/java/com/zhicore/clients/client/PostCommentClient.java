package com.zhicore.api.client;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 评论域所需的文章查询契约。
 */
public interface PostCommentClient extends PostBatchClient {

    @GetMapping("/api/v1/posts/{postId}")
    ApiResponse<PostDTO> getPost(@PathVariable("postId") Long postId);

    @GetMapping("/api/v1/posts/{postId}/exists")
    ApiResponse<Boolean> postExists(@PathVariable("postId") Long postId);
}
