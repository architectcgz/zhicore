package com.zhicore.user.application.service.query;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.user.application.port.UserPostQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户公开文章查询服务。
 */
@Service
@RequiredArgsConstructor
public class UserPostQueryService {

    private final UserPostQueryPort userPostQueryPort;

    public HybridPageResult<PostDTO> getPublishedPostsByAuthor(Long userId, int page, int size) {
        return userPostQueryPort.getPublishedPostsByAuthor(userId, page, size);
    }
}
