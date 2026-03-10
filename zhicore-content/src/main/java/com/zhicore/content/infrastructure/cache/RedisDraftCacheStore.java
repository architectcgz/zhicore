package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.application.port.store.DraftCacheStore;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的草稿缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisDraftCacheStore implements DraftCacheStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public CacheResult<DraftSnapshot> getLatestDraft(Long postId, Long userId) {
        Object cached = redisTemplate.opsForValue().get(PostRedisKeys.draft(postId, userId));
        if (cached == null) {
            return CacheResult.miss();
        }
        if (CacheConstants.isNullMarker(cached)) {
            return CacheResult.nullValue();
        }
        return CacheResult.hit((DraftSnapshot) cached);
    }

    @Override
    public void setLatestDraft(Long postId, Long userId, DraftSnapshot draft, Duration ttl) {
        redisTemplate.opsForValue().set(
                PostRedisKeys.draft(postId, userId),
                draft,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void setLatestDraftNull(Long postId, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(
                PostRedisKeys.draft(postId, userId),
                CacheConstants.NULL_MARKER,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CacheResult<List<DraftSnapshot>> getUserDrafts(Long userId) {
        Object cached = redisTemplate.opsForValue().get(PostRedisKeys.userDrafts(userId));
        if (cached == null) {
            return CacheResult.miss();
        }
        if (CacheConstants.isNullMarker(cached)) {
            return CacheResult.nullValue();
        }
        return CacheResult.hit((List<DraftSnapshot>) cached);
    }

    @Override
    public void setUserDrafts(Long userId, List<DraftSnapshot> drafts, Duration ttl) {
        redisTemplate.opsForValue().set(
                PostRedisKeys.userDrafts(userId),
                drafts,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void setUserDraftsEmpty(Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(
                PostRedisKeys.userDrafts(userId),
                CacheConstants.NULL_MARKER,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void evictDraft(Long postId, Long userId) {
        redisTemplate.delete(PostRedisKeys.draft(postId, userId));
        redisTemplate.delete(PostRedisKeys.userDrafts(userId));
    }
}
