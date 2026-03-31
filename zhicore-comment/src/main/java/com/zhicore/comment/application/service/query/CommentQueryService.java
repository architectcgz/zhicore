package com.zhicore.comment.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
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
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 评论读服务。
 *
 * 负责评论详情和列表查询，不承载任何写操作。
 */
@Service
@RequiredArgsConstructor
public class CommentQueryService {

    private final CommentRepository commentRepository;
    private final CommentDetailCacheService commentDetailCacheService;
    private final CommentViewAssembler commentViewAssembler;
    private final CommentHomepageCacheService commentHomepageCacheService;
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

        return commentViewAssembler.assembleCommentVO(comment);
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
        return commentHomepageCacheService.getTopLevelCommentsPage(
                postId,
                page,
                size,
                sortType,
                commentViewAssembler.getHotRepliesLimit(),
                () -> queryTopLevelCommentsByPage(postId, page, size, sortType)
        );
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

        List<CommentVO> voList = commentViewAssembler.assembleCommentVOList(comments);
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

        List<CommentVO> voList = commentViewAssembler.assembleReplyVOList(replyPage.getRecords());
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

        List<CommentVO> voList = commentViewAssembler.assembleReplyVOList(replies);
        return CursorPage.of(voList, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public PageResult<CommentVO> getTopLevelCommentsIncremental(Long postId,
                                                                String afterCreatedAt,
                                                                Long afterId,
                                                                int size) {
        OffsetDateTime parsedAfterCreatedAt = parseIncrementalCursor(afterCreatedAt, afterId);
        List<Comment> comments = commentRepository.findTopLevelByPostIdIncremental(postId, parsedAfterCreatedAt, afterId, size + 1);
        return buildIncrementalPage(comments, commentViewAssembler::assembleCommentVOList, size);
    }

    @Transactional(readOnly = true)
    public PageResult<CommentVO> getRepliesIncremental(Long rootId,
                                                       String afterCreatedAt,
                                                       Long afterId,
                                                       int size) {
        OffsetDateTime parsedAfterCreatedAt = parseIncrementalCursor(afterCreatedAt, afterId);
        List<Comment> replies = commentRepository.findRepliesByRootIdIncremental(rootId, parsedAfterCreatedAt, afterId, size + 1);
        return buildIncrementalPage(replies, commentViewAssembler::assembleReplyVOList, size);
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

    private OffsetDateTime parseIncrementalCursor(String afterCreatedAt, Long afterId) {
        if (!StringUtils.hasText(afterCreatedAt) && afterId == null) {
            return null;
        }
        if (!StringUtils.hasText(afterCreatedAt) || afterId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "增量游标参数必须同时传入");
        }

        try {
            return OffsetDateTime.parse(afterCreatedAt);
        } catch (Exception ignore) {
            try {
                return parseLegacyIncrementalCursor(afterCreatedAt)
                        .atZoneSameInstant(DateTimeUtils.BUSINESS_ZONE)
                        .toOffsetDateTime();
            } catch (Exception exception) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "无效的时间游标参数");
            }
        }
    }

    private OffsetDateTime parseLegacyIncrementalCursor(String afterCreatedAt) {
        String[] parts = afterCreatedAt.split("T", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid legacy cursor");
        }
        return OffsetDateTime.of(
                LocalDate.parse(parts[0]),
                LocalTime.parse(parts[1]),
                ZoneOffset.UTC
        );
    }

    private PageResult<CommentVO> buildIncrementalPage(List<Comment> comments,
                                                       java.util.function.Function<List<Comment>, List<CommentVO>> assembler,
                                                       int size) {
        boolean hasMore = comments.size() > size;
        String nextCursor = null;
        if (hasMore) {
            comments = comments.subList(0, size);
            Comment lastComment = comments.get(comments.size() - 1);
            nextCursor = timeCursorCodec.encode(lastComment);
        } else if (!comments.isEmpty()) {
            Comment lastComment = comments.get(comments.size() - 1);
            nextCursor = timeCursorCodec.encode(lastComment);
        }

        List<CommentVO> voList = assembler.apply(comments);
        return PageResult.cursor(voList, nextCursor, hasMore);
    }

    private PageResult<CommentVO> queryTopLevelCommentsByPage(Long postId, int page, int size, CommentSortType sortType) {
        PageResult<Comment> commentPage;
        if (sortType == CommentSortType.HOT) {
            commentPage = commentRepository.findTopLevelByPostIdOrderByLikesPage(postId, page, size);
        } else {
            commentPage = commentRepository.findTopLevelByPostIdOrderByTimePage(postId, page, size);
        }
        List<CommentVO> voList = commentViewAssembler.assembleCommentVOList(commentPage.getRecords());
        return PageResult.of(page, size, commentPage.getTotal(), voList);
    }
}
