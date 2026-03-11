package com.zhicore.admin.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.admin.application.dto.PostManageVO;
import com.zhicore.admin.application.sentinel.AdminSentinelHandlers;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import com.zhicore.api.client.AdminPostClient;
import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章管理查询服务。
 */
@Service
@RequiredArgsConstructor
public class PostManageQueryService {

    private final AdminPostClient postServiceClient;

    @SentinelResource(
            value = AdminSentinelResources.LIST_POSTS,
            blockHandlerClass = AdminSentinelHandlers.class,
            blockHandler = "handleListPostsBlocked"
    )
    public PageResult<PostManageVO> listPosts(String keyword, String status, Long authorId, int page, int size) {
        ApiResponse<PageResult<PostManageDTO>> response =
                postServiceClient.queryPosts(keyword, status, authorId, page, size);
        if (!response.isSuccess()) {
            throw new BusinessException(response.getMessage());
        }

        PageResult<PostManageDTO> result = response.getData();
        List<PostManageVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(page, size, result.getTotal(), voList);
    }

    private PostManageVO toVO(PostManageDTO dto) {
        return PostManageVO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .authorId(dto.getAuthorId())
                .authorName(dto.getAuthorName())
                .status(dto.getStatus())
                .viewCount(dto.getViewCount())
                .likeCount(dto.getLikeCount())
                .commentCount(dto.getCommentCount())
                .createdAt(dto.getCreatedAt())
                .publishedAt(dto.getPublishedAt())
                .build();
    }
}
