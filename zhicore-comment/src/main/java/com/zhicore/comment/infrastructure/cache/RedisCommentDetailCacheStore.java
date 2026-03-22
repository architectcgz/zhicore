package com.zhicore.comment.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.port.CacheResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的评论详情缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisCommentDetailCacheStore implements CommentDetailCacheStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CacheResult<Comment> get(Long commentId) {
        Object cached = redisTemplate.opsForValue().get(CommentRedisKeys.detail(commentId));
        if (cached == null) {
            return CacheResult.miss();
        }
        if (CacheConstants.isNullMarker(cached)) {
            return CacheResult.nullValue();
        }
        CommentDetailCacheSnapshot snapshot = objectMapper.convertValue(cached, CommentDetailCacheSnapshot.class);
        return CacheResult.hit(snapshot.toDomain());
    }

    @Override
    public void set(Long commentId, Comment comment, Duration ttl) {
        redisTemplate.opsForValue().set(
                CommentRedisKeys.detail(commentId),
                CommentDetailCacheSnapshot.from(comment),
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void setNull(Long commentId, Duration ttl) {
        redisTemplate.opsForValue().set(
                CommentRedisKeys.detail(commentId),
                CacheConstants.NULL_MARKER,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void evict(Long commentId) {
        redisTemplate.delete(CommentRedisKeys.detail(commentId));
    }
}
