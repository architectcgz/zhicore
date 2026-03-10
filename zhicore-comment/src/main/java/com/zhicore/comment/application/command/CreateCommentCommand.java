package com.zhicore.comment.application.command;

/**
 * 创建评论命令。
 */
public record CreateCommentCommand(
        Long postId,
        String content,
        Long rootId,
        Long replyToCommentId,
        String[] imageIds,
        String voiceId,
        Integer voiceDuration
) {
}
