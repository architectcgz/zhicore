package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.query.model.AdminPostQueryCriteria;
import com.zhicore.content.application.service.query.AdminPostListQueryService;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端文章查询门面。
 *
 * 为管理端接口层收口查询入口，并保留 Sentinel/参数编排这一层职责。
 */
@Service
@RequiredArgsConstructor
public class AdminPostQueryFacade {

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
