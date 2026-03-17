package com.zhicore.admin.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.admin.application.dto.CommentManageVO;
import com.zhicore.admin.application.sentinel.AdminSentinelHandlers;
import com.zhicore.admin.application.sentinel.AdminSentinelResources;
import com.zhicore.api.client.AdminCommentClient;
import com.zhicore.api.dto.admin.CommentManageDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评论管理查询服务。
 */
@Service
@RequiredArgsConstructor
public class CommentManageQueryService {

    private final AdminCommentClient commentServiceClient;

    @SentinelResource(
            value = AdminSentinelResources.LIST_COMMENTS,
            blockHandlerClass = AdminSentinelHandlers.class,
            blockHandler = "handleListCommentsBlocked"
    )
    public PageResult<CommentManageVO> listComments(String keyword, Long postId, Long userId, int page, int size) {
        ApiResponse<PageResult<CommentManageDTO>> response =
                commentServiceClient.queryComments(keyword, postId, userId, page, size);
        if (!response.isSuccess()) {
            throw new BusinessException(response.getMessage());
        }

        PageResult<CommentManageDTO> result = response.getData();
        List<CommentManageVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(page, size, result.getTotal(), voList);
    }

    private CommentManageVO toVO(CommentManageDTO dto) {
        return CommentManageVO.builder()
                .id(dto.getId())
                .postId(dto.getPostId())
                .postTitle(dto.getPostTitle())
                .userId(dto.getUserId())
                .userName(dto.getUserName())
                .content(dto.getContent())
                .likeCount(dto.getLikeCount())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
