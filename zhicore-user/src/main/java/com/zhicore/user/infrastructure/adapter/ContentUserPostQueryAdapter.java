package com.zhicore.user.infrastructure.adapter;

import com.zhicore.api.client.PostServiceClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.user.application.port.UserPostQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 内容服务文章查询适配器。
 */
@Component
@RequiredArgsConstructor
public class ContentUserPostQueryAdapter implements UserPostQueryPort {

    private final PostServiceClient postServiceClient;

    @Override
    public HybridPageResult<PostDTO> getPublishedPostsByAuthor(Long userId, int page, int size) {
        return postServiceClient.getPublishedPostsByAuthor(userId, page, size).getData();
    }
}
