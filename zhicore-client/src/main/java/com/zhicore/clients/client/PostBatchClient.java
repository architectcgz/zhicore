package com.zhicore.api.client;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * 文章批量查询契约。
 */
public interface PostBatchClient {

    @PostMapping("/api/v1/posts/batch")
    ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds);
}
