package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.query.model.AdminPostQueryCriteria;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端文章查询服务。
 */
@Service
@RequiredArgsConstructor
public class AdminPostQueryService {

    private final AdminPostListQueryService adminPostListQueryService;

    @Transactional(readOnly = true)
    @SentinelResource(
            value = ContentSentinelResources.ADMIN_QUERY_POSTS,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleAdminQueryPostsBlocked"
    )
    public PageResult<PostManageDTO> queryPosts(String keyword, String status, Long authorId, int page, int size) {
        return adminPostListQueryService.query(AdminPostQueryCriteria.of(keyword, status, authorId, page, size));
    }
}
