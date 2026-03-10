package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.application.dto.TagStatsDTO;
import com.zhicore.content.application.port.store.TagHotTagsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的热门标签缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisTagHotTagsStore implements TagHotTagsStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheStore cacheStore;

    @Override
    @SuppressWarnings("unchecked")
    public CacheResult<List<TagStatsDTO>> getHotTags(int limit) {
        Object cached = redisTemplate.opsForValue().get(TagRedisKeys.hotTags(limit));
        if (cached == null) {
            return CacheResult.miss();
        }
        if (CacheConstants.isNullMarker(cached)) {
            return CacheResult.nullValue();
        }
        return CacheResult.hit((List<TagStatsDTO>) cached);
    }

    @Override
    public void setHotTags(int limit, List<TagStatsDTO> hotTags, Duration ttl) {
        redisTemplate.opsForValue().set(
                TagRedisKeys.hotTags(limit),
                hotTags,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void setEmptyHotTags(int limit, Duration ttl) {
        redisTemplate.opsForValue().set(
                TagRedisKeys.hotTags(limit),
                CacheConstants.NULL_MARKER,
                ttl.toSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void evictHotTags() {
        cacheStore.deletePattern(TagRedisKeys.hotTagsPattern());
    }
}
