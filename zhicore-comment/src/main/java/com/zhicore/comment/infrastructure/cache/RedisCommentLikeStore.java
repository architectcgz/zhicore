package com.zhicore.comment.infrastructure.cache;

import com.zhicore.comment.application.port.store.CommentLikeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的评论点赞缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisCommentLikeStore implements CommentLikeStore {

    private static final String LIKED_MARKER = "1";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void incrementLikeCount(Long commentId) {
        redisTemplate.opsForValue().increment(CommentRedisKeys.likeCount(commentId));
    }

    @Override
    public void decrementLikeCount(Long commentId) {
        redisTemplate.opsForValue().decrement(CommentRedisKeys.likeCount(commentId));
    }

    @Override
    public Integer getLikeCount(Long commentId) {
        Object count = redisTemplate.opsForValue().get(CommentRedisKeys.likeCount(commentId));
        if (count == null) {
            return null;
        }
        return Integer.parseInt(count.toString());
    }

    @Override
    public void cacheLikeCount(Long commentId, int count) {
        redisTemplate.opsForValue().set(CommentRedisKeys.likeCount(commentId), count);
    }

    @Override
    public Boolean isLiked(Long userId, Long commentId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CommentRedisKeys.userLiked(userId, commentId)))
                ? Boolean.TRUE
                : null;
    }

    @Override
    public Set<Long> findLikedCommentIds(Long userId, List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Set.of();
        }

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long commentId : commentIds) {
                String key = CommentRedisKeys.userLiked(userId, commentId);
                connection.keyCommands().exists(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        Set<Long> likedCommentIds = new HashSet<>();
        for (int i = 0; i < commentIds.size(); i++) {
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result) || (result instanceof Long value && value > 0)) {
                likedCommentIds.add(commentIds.get(i));
            }
        }
        return likedCommentIds;
    }

    @Override
    public void markLiked(Long userId, Long commentId) {
        redisTemplate.opsForValue().set(CommentRedisKeys.userLiked(userId, commentId), LIKED_MARKER);
    }

    @Override
    public void unmarkLiked(Long userId, Long commentId) {
        redisTemplate.delete(CommentRedisKeys.userLiked(userId, commentId));
    }
}
