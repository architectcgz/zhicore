package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.store.PostFavoriteStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的文章收藏缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisPostFavoriteStore implements PostFavoriteStore {

    private static final String FAVORITED_MARKER = "1";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean isFavorited(Long userId, Long postId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PostRedisKeys.userFavorited(userId, postId)))
                ? Boolean.TRUE
                : null;
    }

    @Override
    public Set<Long> findFavoritedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Set.of();
        }

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long postId : postIds) {
                connection.keyCommands().exists(
                        PostRedisKeys.userFavorited(userId, postId).getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });

        Set<Long> favoritedPostIds = new HashSet<>();
        for (int i = 0; i < postIds.size(); i++) {
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result) || (result instanceof Long value && value > 0)) {
                favoritedPostIds.add(postIds.get(i));
            }
        }
        return favoritedPostIds;
    }

    @Override
    public void markFavorited(Long userId, Long postId) {
        redisTemplate.opsForValue().set(PostRedisKeys.userFavorited(userId, postId), FAVORITED_MARKER);
    }

    @Override
    public void unmarkFavorited(Long userId, Long postId) {
        redisTemplate.delete(PostRedisKeys.userFavorited(userId, postId));
    }

    @Override
    public void incrementFavoriteCount(Long postId) {
        redisTemplate.opsForValue().increment(PostRedisKeys.favoriteCount(postId));
    }

    @Override
    public void decrementFavoriteCount(Long postId) {
        redisTemplate.opsForValue().decrement(PostRedisKeys.favoriteCount(postId));
    }

    @Override
    public Integer getFavoriteCount(Long postId) {
        Object count = redisTemplate.opsForValue().get(PostRedisKeys.favoriteCount(postId));
        return count == null ? null : Integer.parseInt(count.toString());
    }

    @Override
    public void cacheFavoriteCount(Long postId, int count) {
        redisTemplate.opsForValue().set(PostRedisKeys.favoriteCount(postId), count);
    }
}
