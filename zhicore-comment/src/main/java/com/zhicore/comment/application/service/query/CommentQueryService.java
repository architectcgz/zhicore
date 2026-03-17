package com.zhicore.comment.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.UserSimpleBatchClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.dto.CursorPage;
import com.zhicore.comment.application.service.CommentDetailCacheService;
import com.zhicore.comment.application.sentinel.CommentSentinelHandlers;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
import com.zhicore.comment.domain.cursor.HotCursorCodec;
import com.zhicore.comment.domain.cursor.HotCursorCodec.HotCursor;
import com.zhicore.comment.domain.cursor.TimeCursorCodec;
import com.zhicore.comment.domain.cursor.TimeCursorCodec.TimeCursor;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论读服务。
 *
 * 负责评论详情和列表查询，不承载任何写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentQueryService {

    private final CommentRepository commentRepository;
    private final CommentDetailCacheService commentDetailCacheService;
    private final UserSimpleBatchClient userServiceClient;
    private final HotCursorCodec hotCursorCodec;
    private final TimeCursorCodec timeCursorCodec;

    /**
     * 获取评论详情。
     *
     * @param commentId 评论 ID
     * @return 评论视图
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

    /**
     * 传统分页查询顶级评论。
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_PAGE,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetTopLevelCommentsPageBlocked"
    )
    @Transactional(readOnly = true)
    public PageResult<CommentVO> getTopLevelCommentsByPage(Long postId, int page, int size, CommentSortType sortType) {
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
     * 游标分页查询顶级评论。
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_TOP_LEVEL_COMMENTS_CURSOR,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetTopLevelCommentsCursorBlocked"
    )
    @Transactional(readOnly = true)
    public CursorPage<CommentVO> getTopLevelCommentsByCursor(Long postId, String cursor, int size, CommentSortType sortType) {
        List<Comment> comments;
        String nextCursor = null;

        if (sortType == CommentSortType.HOT) {
            HotCursor hotCursor = hotCursorCodec.decode(cursor);
            comments = commentRepository.findTopLevelByPostIdOrderByLikesCursor(postId, hotCursor, size + 1);

            boolean hasMore = comments.size() > size;
            if (hasMore) {
                comments = comments.subList(0, size);
                Comment lastComment = comments.get(comments.size() - 1);
                nextCursor = hotCursorCodec.encode(lastComment);
            }
        } else {
            TimeCursor timeCursor = timeCursorCodec.decode(cursor);
            comments = commentRepository.findTopLevelByPostIdOrderByTimeCursor(postId, timeCursor, size + 1);

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

    /**
     * 传统分页查询回复。
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
     * 游标分页查询回复。
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_REPLIES_CURSOR,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetRepliesCursorBlocked"
    )
    @Transactional(readOnly = true)
    public CursorPage<CommentVO> getRepliesByCursor(Long rootId, String cursor, int size) {
        TimeCursor timeCursor = timeCursorCodec.decode(cursor);

        List<Comment> replies = commentRepository.findRepliesByRootIdCursor(rootId, timeCursor, size + 1);

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
                .liked(false)
                .build();
    }

    private List<CommentVO> assembleCommentVOList(List<Comment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(authorIds);

        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        Map<Long, CommentStats> statsMap = commentRepository.batchGetStats(commentIds);
        Map<Long, List<CommentVO>> hotRepliesMap = preloadHotReplies(commentIds);

        return comments.stream()
                .map(comment -> {
                    CommentVO vo = CommentVO.builder()
                            .id(comment.getId())
                            .postId(comment.getPostId())
                            .content(comment.getContent())
                            .imageIds(comment.getImageIds())
                            .voiceId(comment.getVoiceId())
                            .voiceDuration(comment.getVoiceDuration())
                            .author(userMap.get(comment.getAuthorId()))
                            .likeCount(statsMap.getOrDefault(comment.getId(), CommentStats.empty()).getLikeCount())
                            .replyCount(statsMap.getOrDefault(comment.getId(), CommentStats.empty()).getReplyCount())
                            .createdAt(comment.getCreatedAt())
                            .liked(false)
                            .build();
                    vo.setHotReplies(hotRepliesMap.getOrDefault(comment.getId(), Collections.emptyList()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private Map<Long, List<CommentVO>> preloadHotReplies(List<Long> rootIds) {
        if (rootIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<CommentVO>> result = new HashMap<>();
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

        Set<Long> userIds = new HashSet<>();
        replies.forEach(reply -> {
            userIds.add(reply.getAuthorId());
            if (reply.getReplyToUserId() != null) {
                userIds.add(reply.getReplyToUserId());
            }
        });
        Map<Long, UserSimpleDTO> userMap = batchGetUsers(userIds);

        return replies.stream()
                .map(reply -> CommentVO.builder()
                        .id(reply.getId())
                        .postId(reply.getPostId())
                        .rootId(reply.getRootId())
                        .content(reply.getContent())
                        .imageIds(reply.getImageIds())
                        .voiceId(reply.getVoiceId())
                        .voiceDuration(reply.getVoiceDuration())
                        .author(userMap.get(reply.getAuthorId()))
                        .replyToUser(reply.getReplyToUserId() != null ? userMap.get(reply.getReplyToUserId()) : null)
                        .likeCount(reply.getStats().getLikeCount())
                        .createdAt(reply.getCreatedAt())
                        .liked(false)
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
}
