package com.zhicore.comment.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.PostCommentClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.event.comment.CommentCreatedEvent;
import com.zhicore.api.event.comment.CommentDeletedEvent;
import com.zhicore.comment.application.command.CreateCommentCommand;
import com.zhicore.comment.application.command.UpdateCommentCommand;
import com.zhicore.comment.application.port.event.CommentEventPort;
import com.zhicore.comment.application.port.store.CommentCounterStore;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 评论写服务。
 *
 * 负责评论创建、编辑、删除等命令侧操作，不承载任何查询职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentCommandService {

    private final CommentRepository commentRepository;
    private final CommentDetailCacheStore commentDetailCacheStore;
    private final CommentCounterStore commentCounterStore;
    private final CommentStatsRepository commentStatsRepository;
    private final CommentEventPort eventPublisher;
    private final PostCommentClient postServiceClient;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建评论。
     *
     * @param authorId 作者 ID
     * @param request 创建请求
     * @return 评论 ID
     */
    public Long createComment(Long authorId, CreateCommentCommand request) {
        ApiResponse<PostDTO> postResponse = postServiceClient.getPost(request.postId());
        if (!postResponse.isSuccess() || postResponse.getData() == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND, "文章不存在");
        }
        PostDTO post = postResponse.getData();

        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("生成评论ID失败: {}", idResponse.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "评论ID生成失败");
        }
        Long commentId = idResponse.getData();

        final Comment[] commentHolder = new Comment[1];
        final Long rootId = request.rootId();

        transactionTemplate.executeWithoutResult(status -> {
            Comment comment;

            if (rootId == null) {
                comment = Comment.createTopLevel(
                        commentId, request.postId(), authorId, request.content(),
                        request.imageIds(), request.voiceId(), request.voiceDuration()
                );
            } else {
                Comment rootComment = commentRepository.findById(rootId)
                        .orElseThrow(() -> new BusinessException(ResultCode.ROOT_COMMENT_NOT_FOUND));

                if (!rootComment.isTopLevel()) {
                    throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "只能回复顶级评论");
                }

                if (rootComment.isDeleted()) {
                    throw new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "不能回复已删除的评论");
                }

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
                    replyToUserId = rootComment.getAuthorId();
                }

                comment = Comment.createReply(
                        commentId, request.postId(), authorId, request.content(),
                        request.imageIds(), request.voiceId(), request.voiceDuration(),
                        rootId, replyToUserId
                );
                commentStatsRepository.incrementReplyCount(rootId);
            }

            commentRepository.save(comment);
            commentHolder[0] = comment;
        });

        Comment comment = commentHolder[0];

        try {
            if (rootId != null) {
                commentCounterStore.incrementReplyCount(rootId);
            }
            commentCounterStore.incrementPostCommentCount(request.postId());
        } catch (Exception e) {
            log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
        }

        eventPublisher.publishCommentCreated(new CommentCreatedEvent(
                commentId, request.postId(), post.getOwnerId(),
                authorId, rootId, comment.getReplyToUserId(),
                truncateContent(request.content(), 100)
        ));

        log.info("Comment created: commentId={}, postId={}, authorId={}, rootId={}",
                commentId, request.postId(), authorId, rootId);

        return commentId;
    }

    /**
     * 更新评论。
     *
     * @param currentUserId 当前用户 ID
     * @param commentId 评论 ID
     * @param request 更新请求
     */
    @Transactional
    public void updateComment(Long currentUserId, Long commentId, UpdateCommentCommand request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        comment.edit(request.content(), currentUserId);
        commentRepository.update(comment);
        commentDetailCacheStore.evict(commentId);

        log.info("Comment updated: commentId={}, userId={}", commentId, currentUserId);
    }

    /**
     * 删除评论。
     *
     * @param currentUserId 当前用户 ID
     * @param isAdmin 是否管理员操作
     * @param commentId 评论 ID
     */
    @Transactional
    public void deleteComment(Long currentUserId, boolean isAdmin, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        comment.delete(currentUserId, isAdmin);
        commentRepository.update(comment);

        if (comment.isReply()) {
            commentStatsRepository.decrementReplyCount(comment.getRootId());
            try {
                commentCounterStore.decrementReplyCount(comment.getRootId());
            } catch (Exception e) {
                log.warn("Redis 更新失败: {}", e.getMessage());
            }
        }

        try {
            commentCounterStore.decrementPostCommentCount(comment.getPostId());
        } catch (Exception e) {
            log.warn("Redis 更新失败: {}", e.getMessage());
        }

        try {
            commentDetailCacheStore.evict(commentId);
        } catch (Exception e) {
            log.warn("Redis 缓存清除失败: {}", e.getMessage());
        }

        eventPublisher.publishCommentDeleted(new CommentDeletedEvent(
                commentId, comment.getPostId(), comment.getAuthorId(),
                comment.isTopLevel(), isAdmin ? "ADMIN" : "AUTHOR"
        ));

        log.info("Comment deleted: commentId={}, userId={}, isAdmin={}", commentId, currentUserId, isAdmin);
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
