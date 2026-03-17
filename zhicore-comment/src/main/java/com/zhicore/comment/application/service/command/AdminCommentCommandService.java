package com.zhicore.comment.application.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理侧评论写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentCommandService {

    private static final Long ADMIN_OPERATOR_ID = 0L;

    private final CommentCommandService commentCommandService;

    /**
     * 删除评论。
     *
     * @param commentId 评论 ID
     */
    public void deleteComment(Long commentId) {
        commentCommandService.deleteComment(ADMIN_OPERATOR_ID, true, commentId);
        log.info("Admin deleted comment: commentId={}", commentId);
    }
}
