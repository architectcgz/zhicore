package com.zhicore.comment.application.service;

import com.zhicore.api.event.comment.CommentDeletedEvent;
import com.zhicore.comment.application.port.event.CommentEventPort;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理侧评论写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentCommandService {

    private final CommentRepository commentRepository;
    private final CommentEventPort eventPublisher;

    /**
     * 删除评论。
     *
     * @param commentId 评论 ID
     */
    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "评论不存在"));

        comment.delete(comment.getAuthorId(), true);
        commentRepository.update(comment);

        eventPublisher.publishCommentDeleted(new CommentDeletedEvent(
                commentId, comment.getPostId(), comment.getAuthorId(),
                comment.isTopLevel(), "ADMIN"
        ));

        log.info("Admin deleted comment: commentId={}", commentId);
    }
}
