package com.zhicore.comment.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.PostCommentClient;
import com.zhicore.api.client.UserSimpleBatchClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.api.event.comment.CommentCreatedEvent;
import com.zhicore.api.event.comment.CommentDeletedEvent;
import com.zhicore.comment.application.command.CreateCommentCommand;
import com.zhicore.comment.application.command.UpdateCommentCommand;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.dto.CursorPage;
import com.zhicore.comment.application.port.event.CommentEventPort;
import com.zhicore.comment.application.port.store.CommentCounterStore;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.domain.cursor.HotCursorCodec;
import com.zhicore.comment.domain.cursor.HotCursorCodec.HotCursor;
import com.zhicore.comment.domain.cursor.TimeCursorCodec;
import com.zhicore.comment.domain.cursor.TimeCursorCodec.TimeCursor;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.comment.application.sentinel.CommentSentinelHandlers;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentApplicationService {

    private final CommentRepository commentRepository;
    private final CommentDetailCacheService commentDetailCacheService;
    private final CommentDetailCacheStore commentDetailCacheStore;
    private final CommentCounterStore commentCounterStore;
    private final CommentStatsRepository commentStatsRepository;
    private final CommentEventPort eventPublisher;
    private final PostCommentClient postServiceClient;
    private final UserSimpleBatchClient userServiceClient;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final HotCursorCodec hotCursorCodec;
    private final TimeCursorCodec timeCursorCodec;
    private final TransactionTemplate transactionTemplate;

    // ==================== 创建评论 ====================

    /**
     * 创建评论
     *
     * @param authorId 作者ID
     * @param request 创建请求
     * @return 评论ID
     */
    public Long createComment(Long authorId, CreateCommentCommand request) {
        // 验证文章存在并获取文章信息
        ApiResponse<PostDTO> postResponse = postServiceClient.getPost(request.postId());
        if (!postResponse.isSuccess() || postResponse.getData() == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND, "文章不存在");
        }
        PostDTO post = postResponse.getData();

        // 生成评论ID
        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("生成评论ID失败: {}", idResponse.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "评论ID生成失败");
        }
        Long commentId = idResponse.getData();
        
        final Comment[] commentHolder = new Comment[1];
        final Long rootId = request.rootId();

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            Comment comment;

            if (rootId == null) {
                // 创建顶级评论
                comment = Comment.createTopLevel(
                        commentId, request.postId(), authorId, request.content(),
                        request.imageIds(), request.voiceId(), request.voiceDuration()
                );
            } else {
                // 创建回复评论
                Comment rootComment = commentRepository.findById(rootId)
                        .orElseThrow(() -> new BusinessException(ResultCode.ROOT_COMMENT_NOT_FOUND));
                
                if (!rootComment.isTopLevel()) {
                    throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "只能回复顶级评论");
                }
                
                if (rootComment.isDeleted()) {
                    throw new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "不能回复已删除的评论");
                }

                // 确定被回复用户
                Long replyToUserId;
                Long replyToCommentId = request.replyToCommentId();
                if (replyToCommentId != null) {
                    Comment replyToComment = commentRepository.findById(replyToCommentId)
                            .orElseThrow(() -> new BusinessException(ResultCode.REPLY_TO_COMMENT_NOT_FOUND));
                    if (replyToComment.isDeleted()) {
                        throw new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "不能回复已删除的评论");
                    }
                    replyToUserId = replyToComment.getAuthorId();
                } else {
                    // 直接回复顶级评论
                    replyToUserId = rootComment.getAuthorId();
                }

                comment = Comment.createReply(
                        commentId, request.postId(), authorId, request.content(),
                        request.imageIds(), request.voiceId(), request.voiceDuration(),
                        rootId, replyToUserId
                );

                // 更新顶级评论的回复数（数据库）
                commentStatsRepository.incrementReplyCount(rootId);
            }

            commentRepository.save(comment);
            commentHolder[0] = comment;
        });

        Comment comment = commentHolder[0];

        // 事务提交成功后，更新 Redis 缓存
        try {
            if (rootId != null) {
                // 更新顶级评论的回复计数
                commentCounterStore.incrementReplyCount(rootId);
            }

            // 更新文章评论计数
            commentCounterStore.incrementPostCommentCount(request.postId());
        } catch (Exception e) {
            log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
        }

        // 发布事件
        eventPublisher.publishCommentCreated(new CommentCreatedEvent(
                commentId, request.postId(), post.getOwnerId(),
                authorId, rootId, comment.getReplyToUserId(),
                truncateContent(request.content(), 100)
        ));

        log.info("Comment created: commentId={}, postId={}, authorId={}, rootId={}",
                commentId, request.postId(), authorId, rootId);

        return commentId;
    }

    // ==================== 更新评论 ====================

    /**
     * 更新评论
     *
     * @param commentId 评论ID
     * @param request 更新请求
     */
    @Transactional
    public void updateComment(Long currentUserId, Long commentId, UpdateCommentCommand request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        comment.edit(request.content(), currentUserId);
        commentRepository.update(comment);

        // 清除缓存
        commentDetailCacheStore.evict(commentId);

        log.info("Comment updated: commentId={}, userId={}", commentId, currentUserId);
    }

    // ==================== 删除评论 ====================

    /**
     * 删除评论
     *
     * @param commentId 评论ID
     */
    @Transactional
    public void deleteComment(Long currentUserId, boolean isAdmin, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        comment.delete(currentUserId, isAdmin);
        commentRepository.update(comment);

        // 如果是回复，更新顶级评论的回复数
        if (comment.isReply()) {
            commentStatsRepository.decrementReplyCount(comment.getRootId());
            try {
                commentCounterStore.decrementReplyCount(comment.getRootId());
            } catch (Exception e) {
                log.warn("Redis 更新失败: {}", e.getMessage());
            }
        }

        // 更新文章评论计数
        try {
            commentCounterStore.decrementPostCommentCount(comment.getPostId());
        } catch (Exception e) {
            log.warn("Redis 更新失败: {}", e.getMessage());
        }

        // 清除缓存
        try {
            commentDetailCacheStore.evict(commentId);
        } catch (Exception e) {
            log.warn("Redis 缓存清除失败: {}", e.getMessage());
        }

        // 发布评论删除事件
        eventPublisher.publishCommentDeleted(new CommentDeletedEvent(
                commentId, comment.getPostId(), comment.getAuthorId(),
                comment.isTopLevel(), isAdmin ? "ADMIN" : "AUTHOR"
        ));

        log.info("Comment deleted: commentId={}, userId={}, isAdmin={}", commentId, currentUserId, isAdmin);
    }

    // ==================== 获取评论详情 ====================

    /**
     * 获取评论详情
     *
     * @param commentId 评论ID
     * @return 评论VO
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_COMMENT_DETAIL,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetCommentBlocked"
    )
    @Transactional(readOnly = true)
    public CommentVO getComment(Long commentId) {
        Comment comment = commentDetailCacheService.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        return assembleCommentVO(comment);
    }

    // ==================== 顶级评论查询 ====================

    /**
     * 【Web端】获取文章的顶级评论 - 传统分页
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_PAGE,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetTopLevelCommentsPageBlocked"
    )
    @Transactional(readOnly = true)
    public PageResult<CommentVO> getTopLevelCommentsByPage(Long postId, int page, int size,
                                                           CommentSortType sortType) {
        validateTraditionalPaginationWindow(page, size);
        PageResult<Comment> commentPage;

        if (sortType == CommentSortType.HOT) {
            commentPage = commentRepository.findTopLevelByPostIdOrderByLikesPage(postId, page, size);
        } else {
            commentPage = commentRepository.findTopLevelByPostIdOrderByTimePage(postId, page, size);
        }

        List<CommentVO> voList = assembleCommentVOList(commentPage.getRecords());
        return PageResult.of(page, size, commentPage.getTotal(), voList);
    }

    /**
     * 【移动端】获取文章的顶级评论 - 游标分页
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_CURSOR,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetTopLevelCommentsCursorBlocked"
    )
    @Transactional(readOnly = true)
    public CursorPage<CommentVO> getTopLevelCommentsByCursor(Long postId, String cursor, int size,
                                                              CommentSortType sortType) {
        List<Comment> comments;
        String nextCursor = null;

        if (sortType == CommentSortType.HOT) {
            HotCursor hotCursor = hotCursorCodec.decode(cursor);
            comments = commentRepository.findTopLevelByPostIdOrderByLikesCursor(
                    postId, hotCursor, size + 1  // 多查一条判断是否有下一页
            );

            boolean hasMore = comments.size() > size;
            if (hasMore) {
                comments = comments.subList(0, size);
                Comment lastComment = comments.get(comments.size() - 1);
                nextCursor = hotCursorCodec.encode(lastComment);
            }
        } else {
            TimeCursor timeCursor = timeCursorCodec.decode(cursor);
            comments = commentRepository.findTopLevelByPostIdOrderByTimeCursor(
                    postId, timeCursor, size + 1
            );

            boolean hasMore = comments.size() > size;
            if (hasMore) {
                comments = comments.subList(0, size);
                Comment lastComment = comments.get(comments.size() - 1);
                nextCursor = timeCursorCodec.encode(lastComment);
            }
        }

        List<CommentVO> voList = assembleCommentVOList(comments);
        return CursorPage.of(voList, nextCursor, nextCursor != null);
    }

    // ==================== 回复列表查询 ====================

    /**
     * 【Web端】获取评论的回复列表 - 传统分页
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_REPLIES_PAGE,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetRepliesPageBlocked"
    )
    @Transactional(readOnly = true)
    public PageResult<CommentVO> getRepliesByPage(Long rootId, int page, int size) {
        validateTraditionalPaginationWindow(page, size);
        PageResult<Comment> replyPage = commentRepository.findRepliesByRootIdPage(rootId, page, size);

        List<CommentVO> voList = assembleReplyVOList(replyPage.getRecords());
        return PageResult.of(page, size, replyPage.getTotal(), voList);
    }

    /**
     * 【移动端】获取评论的回复列表 - 游标分页
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_REPLIES_CURSOR,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetRepliesCursorBlocked"
    )
    @Transactional(readOnly = true)
    public CursorPage<CommentVO> getRepliesByCursor(Long rootId, String cursor, int size) {
        TimeCursor timeCursor = timeCursorCodec.decode(cursor);

        List<Comment> replies = commentRepository.findRepliesByRootIdCursor(
                rootId, timeCursor, size + 1
        );

        boolean hasMore = replies.size() > size;
        String nextCursor = null;
        if (hasMore) {
            replies = replies.subList(0, size);
            Comment lastReply = replies.get(replies.size() - 1);
            nextCursor = timeCursorCodec.encode(lastReply);
        }

        List<CommentVO> voList = assembleReplyVOList(replies);
        return CursorPage.of(voList, nextCursor, hasMore);
    }

    // ==================== 辅助方法 ====================

    private void validateTraditionalPaginationWindow(int page, int size) {
        long offset = (long) page * size;
        if (offset >= CommonConstants.MAX_OFFSET_WINDOW) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    "传统分页仅支持前" + CommonConstants.MAX_OFFSET_WINDOW + "条数据，请改用游标分页"
            );
        }
    }

    private CommentVO assembleCommentVO(Comment comment) {
        // 获取用户信息
        UserSimpleDTO author = fetchUserOrNull(comment.getAuthorId());

        UserSimpleDTO replyToUser = null;
        if (comment.getReplyToUserId() != null) {
            replyToUser = fetchUserOrNull(comment.getReplyToUserId());
        }

        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .rootId(comment.isTopLevel() ? null : comment.getRootId())
                .content(comment.getContent())
                .imageIds(comment.getImageIds())
                .voiceId(comment.getVoiceId())
                .voiceDuration(comment.getVoiceDuration())
                .author(author)
                .replyToUser(replyToUser)
                .likeCount(comment.getStats().getLikeCount())
                .replyCount(comment.getStats().getReplyCount())
                .createdAt(comment.getCreatedAt())
                .liked(false)  // 需要单独查询
                .build();
    }

    private List<CommentVO> assembleCommentVOList(List<Comment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取用户信息
        Set<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(authorIds);

        // 批量获取统计信息
        List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
        Map<Long, CommentStats> statsMap = commentRepository.batchGetStats(commentIds);

        // 批量预加载热门回复
        Map<Long, List<CommentVO>> hotRepliesMap = preloadHotReplies(commentIds);

        return comments.stream()
                .map(c -> {
                    CommentVO vo = CommentVO.builder()
                            .id(c.getId())
                            .postId(c.getPostId())
                            .content(c.getContent())
                            .imageIds(c.getImageIds())
                            .voiceId(c.getVoiceId())
                            .voiceDuration(c.getVoiceDuration())
                            .author(userMap.get(c.getAuthorId()))
                            .likeCount(statsMap.getOrDefault(c.getId(), CommentStats.empty()).getLikeCount())
                            .replyCount(statsMap.getOrDefault(c.getId(), CommentStats.empty()).getReplyCount())
                            .createdAt(c.getCreatedAt())
                            .liked(false)  // 需要单独查询
                            .build();
                    vo.setHotReplies(hotRepliesMap.getOrDefault(c.getId(), Collections.emptyList()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 预加载热门回复
     */
    private Map<Long, List<CommentVO>> preloadHotReplies(List<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<CommentVO>> result = new HashMap<>();
        
        // 为每个顶级评论加载热门回复
        for (Long rootId : rootIds) {
            List<Comment> hotReplies = commentRepository.findHotRepliesByRootId(rootId, 3);
            if (!hotReplies.isEmpty()) {
                result.put(rootId, assembleReplyVOList(hotReplies));
            }
        }

        return result;
    }

    private List<CommentVO> assembleReplyVOList(List<Comment> replies) {
        if (replies.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取用户信息（包括被回复用户）
        Set<Long> userIds = new HashSet<>();
        replies.forEach(r -> {
            userIds.add(r.getAuthorId());
            if (r.getReplyToUserId() != null) {
                userIds.add(r.getReplyToUserId());
            }
        });
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(userIds);

        return replies.stream()
                .map(r -> CommentVO.builder()
                        .id(r.getId())
                        .postId(r.getPostId())
                        .rootId(r.getRootId())
                        .content(r.getContent())
                        .imageIds(r.getImageIds())
                        .voiceId(r.getVoiceId())
                        .voiceDuration(r.getVoiceDuration())
                        .author(userMap.get(r.getAuthorId()))
                        .replyToUser(r.getReplyToUserId() != null ? userMap.get(r.getReplyToUserId()) : null)
                        .likeCount(r.getStats().getLikeCount())
                        .createdAt(r.getCreatedAt())
                        .liked(false)  // 需要单独查询
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Long, UserSimpleDTO> batchGetUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ApiResponse<Map<Long, UserSimpleDTO>> response = userServiceClient.batchGetUsersSimple(userIds);
            return response != null && response.isSuccess() && response.getData() != null
                    ? response.getData()
                    : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("批量获取评论用户信息失败: userIds={}, error={}", userIds, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private UserSimpleDTO fetchUserOrNull(Long userId) {
        try {
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(userId);
            return response != null && response.isSuccess() ? response.getData() : null;
        } catch (Exception e) {
            log.warn("获取评论用户信息失败: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
