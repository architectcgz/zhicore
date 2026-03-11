package com.zhicore.comment.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.PostBatchClient;
import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.dto.admin.CommentManageDTO;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.application.sentinel.CommentSentinelHandlers;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.QueryParamValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理侧评论读服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentQueryService {

    private static final String USER_SERVICE_DEGRADED_MESSAGE = "用户服务已降级";
    private static final String POST_SERVICE_DEGRADED_MESSAGE = "文章服务已降级";

    private final CommentRepository commentRepository;
    private final UserBatchSimpleClient userServiceClient;
    private final PostBatchClient postServiceClient;

    /**
     * 查询评论列表。
     */
    @SentinelResource(
            value = CommentSentinelResources.ADMIN_QUERY_COMMENTS,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleAdminQueryCommentsBlocked"
    )
    @Transactional(readOnly = true)
    public PageResult<CommentManageDTO> queryComments(String keyword, Long postId, Long userId, int page, int size) {
        try {
            keyword = QueryParamValidator.validateKeyword(keyword);
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);

            int offset = QueryParamValidator.calculateOffset(page, size);
            List<Comment> comments = commentRepository.findByConditions(keyword, postId, userId, offset, size);
            long total = commentRepository.countByConditions(keyword, postId, userId);

            Set<Long> userIds = comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
            Set<Long> postIds = comments.stream().map(Comment::getPostId).collect(Collectors.toSet());

            Map<Long, UserSimpleDTO> userMap = batchGetUsers(userIds);
            Map<Long, PostDTO> postMap = batchGetPosts(postIds);

            List<CommentManageDTO> dtoList = comments.stream()
                    .map(comment -> convertToManageDTO(comment, userMap, postMap))
                    .collect(Collectors.toList());

            log.debug("Query comments: keyword={}, postId={}, userId={}, page={}, size={}, total={}",
                    keyword, postId, userId, page, size, total);
            return PageResult.of(page, size, total, dtoList);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query comments: keyword={}, postId={}, userId={}, page={}, size={}",
                    keyword, postId, userId, page, size, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "查询评论列表失败");
        }
    }

    private Map<Long, UserSimpleDTO> batchGetUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        try {
            ApiResponse<Map<Long, UserSimpleDTO>> response = userServiceClient.batchGetUsersSimple(userIds);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to batch get users", e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
        }
        throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
    }

    private Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        try {
            ApiResponse<Map<Long, PostDTO>> response = postServiceClient.batchGetPosts(postIds);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to batch get posts", e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, POST_SERVICE_DEGRADED_MESSAGE);
        }
        throw new BusinessException(ResultCode.SERVICE_DEGRADED, POST_SERVICE_DEGRADED_MESSAGE);
    }

    private CommentManageDTO convertToManageDTO(Comment comment, Map<Long, UserSimpleDTO> userMap, Map<Long, PostDTO> postMap) {
        UserSimpleDTO user = userMap.get(comment.getAuthorId());
        PostDTO post = postMap.get(comment.getPostId());

        return CommentManageDTO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .postTitle(post != null ? post.getTitle() : "")
                .userId(comment.getAuthorId())
                .userName(user != null ? user.getNickname() : "")
                .content(comment.getContent())
                .likeCount(comment.getStats().getLikeCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
