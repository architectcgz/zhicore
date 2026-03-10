package com.zhicore.comment.application.port.store;

/**
 * 评论计数缓存存储端口。
 *
 * 封装回复数、文章评论数等缓存操作，
 * 避免应用层直接依赖 RedisTemplate 和 key 细节。
 */
public interface CommentCounterStore {

    void incrementReplyCount(Long commentId);

    void decrementReplyCount(Long commentId);

    void incrementPostCommentCount(Long postId);

    void decrementPostCommentCount(Long postId);
}
