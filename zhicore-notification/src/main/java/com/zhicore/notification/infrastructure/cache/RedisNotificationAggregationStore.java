package com.zhicore.notification.infrastructure.cache;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的通知聚合缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisNotificationAggregationStore implements NotificationAggregationStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheStore cacheStore;

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<AggregatedNotificationVO> get(Long userId, int page, int size) {
        Object cached = redisTemplate.opsForValue().get(
                NotificationRedisKeys.aggregatedList(String.valueOf(userId), page, size)
        );
        if (cached == null) {
            return null;
        }
        return (PageResult<AggregatedNotificationVO>) cached;
    }

    @Override
    public void set(Long userId, int page, int size, PageResult<AggregatedNotificationVO> result, Duration ttl) {
        redisTemplate.opsForValue().set(
                NotificationRedisKeys.aggregatedList(String.valueOf(userId), page, size),
                result,
                ttl
        );
    }

    @Override
    public void evictUser(Long userId) {
        cacheStore.deletePattern(NotificationRedisKeys.aggregatedListPattern(String.valueOf(userId)));
    }
}
