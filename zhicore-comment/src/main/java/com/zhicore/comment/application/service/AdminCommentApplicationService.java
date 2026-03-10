package com.zhicore.comment.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.PostBatchClient;
import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.dto.admin.CommentManageDTO;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.api.event.comment.CommentDeletedEvent;
import com.zhicore.comment.application.port.event.CommentEventPort;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.application.sentinel.CommentSentinelHandlers;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
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
 * 管理员评论管理应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentApplicationService {

    private static final String USER_SERVICE_DEGRADED_MESSAGE = "用户服务已降级";
    private static final String POST_SERVICE_DEGRADED_MESSAGE = "文章服务已降级";

    private final CommentRepository commentRepository;
    private final UserBatchSimpleClient userServiceClient;
    private final PostBatchClient postServiceClient;
    private final CommentEventPort eventPublisher;

    /**
     * 查询评论列表
     *
     * @param keyword 关键词
     * @param postId 文章ID
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 评论列表
     */
    @SentinelResource(
            value = CommentSentinelResources.ADMIN_QUERY_COMMENTS,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleAdminQueryCommentsBlocked"
    )
    @Transactional(readOnly = true)
    public PageResult<CommentManageDTO> queryComments(String keyword, Long postId, Long userId, int page, int size) {
        try {
            // 参数验证和规范化
            keyword = QueryParamValidator.validateKeyword(keyword);
            // postId and userId are already Long, no need to validate as String
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);

            // 计算偏移量
            int offset = QueryParamValidator.calculateOffset(page, size);

            // 执行数据库查询
            List<Comment> comments = commentRepository.findByConditions(keyword, postId, userId, offset, size);
            long total = commentRepository.countByConditions(keyword, postId, userId);

            // 批量获取用户和文章信息
            Set<Long> userIds = comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
            Set<Long> postIds = comments.stream().map(Comment::getPostId).collect(Collectors.toSet());

            Map<Long, UserSimpleDTO> userMap = batchGetUsers(userIds);
            Map<Long, PostDTO> postMap = batchGetPosts(postIds);

            // 转换为 DTO
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

    /**
     * 删除评论（软删除）
     *
     * @param commentId 评论ID
     */
    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "评论不存在"));

        // 管理员可以删除任何评论
        comment.delete(comment.getAuthorId(), true);
        commentRepository.update(comment);

        // 发布评论删除事件
        eventPublisher.publishCommentDeleted(new CommentDeletedEvent(
                commentId, comment.getPostId(), comment.getAuthorId(),
                comment.isTopLevel(), "ADMIN"
        ));

        log.info("Admin deleted comment: commentId={}", commentId);
    }

    /**
     * 批量获取用户信息
     */
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

    /**
     * 批量获取文章信息
     */
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

    /**
     * 转换为管理 DTO
     */
    private CommentManageDTO convertToManageDTO(Comment comment, 
                                                Map<Long, UserSimpleDTO> userMap,
                                                Map<Long, PostDTO> postMap) {
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
