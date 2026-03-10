package com.zhicore.comment.infrastructure.cache;

import com.zhicore.comment.application.port.store.CommentCounterStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的评论计数缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisCommentCounterStore implements CommentCounterStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void incrementReplyCount(Long commentId) {
        redisTemplate.opsForValue().increment(CommentRedisKeys.replyCount(commentId));
    }

    @Override
    public void decrementReplyCount(Long commentId) {
        redisTemplate.opsForValue().decrement(CommentRedisKeys.replyCount(commentId));
    }

    @Override
    public void incrementPostCommentCount(Long postId) {
        redisTemplate.opsForValue().increment(CommentRedisKeys.postCommentCount(postId));
    }

    @Override
    public void decrementPostCommentCount(Long postId) {
        redisTemplate.opsForValue().decrement(CommentRedisKeys.postCommentCount(postId));
    }
}
