package com.zhicore.comment.application.service.command;

import com.zhicore.comment.application.port.store.CommentLikeStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 评论点赞写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentLikeCommandService {

    private final CommentLikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final CommentStatsRepository commentStatsRepository;
    private final CommentLikeStore commentLikeStore;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * 点赞评论。
     */
    public void likeComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "评论已删除，无法点赞");
        }

        boolean[] actuallyInserted = {false};
        transactionTemplate.executeWithoutResult(status -> {
            actuallyInserted[0] = likeRepository.insertIfAbsent(commentId, userId);
            if (actuallyInserted[0]) {
                commentStatsRepository.incrementLikeCount(commentId);
            }
        });

        try {
            if (actuallyInserted[0]) {
                commentLikeStore.incrementLikeCount(commentId);
            }
            commentLikeStore.markLiked(userId, commentId);
        } catch (Exception e) {
            handleCacheUpdateFailure("like", commentId, userId, e);
        }

        log.info("Comment liked: commentId={}, userId={}, actuallyInserted={}",
                commentId, userId, actuallyInserted[0]);
    }

    /**
     * 取消点赞评论。
     */
    public void unlikeComment(Long userId, Long commentId) {
        boolean[] actuallyDeleted = {false};
        transactionTemplate.executeWithoutResult(status -> {
            actuallyDeleted[0] = likeRepository.deleteAndReturnAffected(commentId, userId);
            if (actuallyDeleted[0]) {
                commentStatsRepository.decrementLikeCount(commentId);
            }
        });

        try {
            if (actuallyDeleted[0]) {
                commentLikeStore.decrementLikeCount(commentId);
            }
            commentLikeStore.unmarkLiked(userId, commentId);
        } catch (Exception e) {
            handleCacheUpdateFailure("unlike", commentId, userId, e);
        }

        log.info("Comment unliked: commentId={}, userId={}, actuallyDeleted={}",
                commentId, userId, actuallyDeleted[0]);
    }

    private void handleCacheUpdateFailure(String operation, Long commentId, Long userId, Exception e) {
        log.warn("Redis 更新失败: operation={}, commentId={}, userId={}, error={}",
                operation, commentId, userId, e.getMessage());
        meterRegistry.counter("cache.update.failure",
                "operation", operation,
                "service", "comment-service"
        ).increment();
    }
}
