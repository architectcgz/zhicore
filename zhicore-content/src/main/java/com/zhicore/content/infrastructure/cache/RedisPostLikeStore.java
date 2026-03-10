package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.store.PostLikeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的文章点赞缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisPostLikeStore implements PostLikeStore {

    private static final String LIKED_MARKER = "1";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean isLiked(Long userId, Long postId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PostRedisKeys.userLiked(userId, postId)))
                ? Boolean.TRUE
                : null;
    }

    @Override
    public Set<Long> findLikedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Set.of();
        }

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long postId : postIds) {
                connection.keyCommands().exists(
                        PostRedisKeys.userLiked(userId, postId).getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });

        Set<Long> likedPostIds = new HashSet<>();
        for (int i = 0; i < postIds.size(); i++) {
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result) || (result instanceof Long value && value > 0)) {
                likedPostIds.add(postIds.get(i));
            }
        }
        return likedPostIds;
    }

    @Override
    public void markLiked(Long userId, Long postId) {
        redisTemplate.opsForValue().set(PostRedisKeys.userLiked(userId, postId), LIKED_MARKER);
    }

    @Override
    public void unmarkLiked(Long userId, Long postId) {
        redisTemplate.delete(PostRedisKeys.userLiked(userId, postId));
    }

    @Override
    public void incrementLikeCount(Long postId) {
        redisTemplate.opsForValue().increment(PostRedisKeys.likeCount(postId));
    }

    @Override
    public void decrementLikeCount(Long postId) {
        redisTemplate.opsForValue().decrement(PostRedisKeys.likeCount(postId));
    }

    @Override
    public Integer getLikeCount(Long postId) {
        Object count = redisTemplate.opsForValue().get(PostRedisKeys.likeCount(postId));
        return count == null ? null : Integer.parseInt(count.toString());
    }

    @Override
    public void cacheLikeCount(Long postId, int count) {
        redisTemplate.opsForValue().set(PostRedisKeys.likeCount(postId), count);
    }
}
