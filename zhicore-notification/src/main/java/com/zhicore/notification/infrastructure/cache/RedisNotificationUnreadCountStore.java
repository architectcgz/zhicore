package com.zhicore.notification.infrastructure.cache;

import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的通知未读数缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisNotificationUnreadCountStore implements NotificationUnreadCountStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Integer get(Long userId) {
        Object cached = redisTemplate.opsForValue().get(NotificationRedisKeys.unreadCount(userId));
        return cached instanceof Number value ? value.intValue() : null;
    }

    @Override
    public void set(Long userId, int count, Duration ttl) {
        redisTemplate.opsForValue().set(NotificationRedisKeys.unreadCount(userId), count, ttl);
    }

    @Override
    public Long increment(Long userId, long delta, Duration ttl) {
        String key = NotificationRedisKeys.unreadCount(userId);
        Long value = redisTemplate.opsForValue().increment(key, delta);
        if (ttl != null) {
            redisTemplate.expire(key, ttl);
        }
        return value;
    }

    @Override
    public Long decrement(Long userId, long delta) {
        String key = NotificationRedisKeys.unreadCount(userId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return null;
        }
        Long value = redisTemplate.opsForValue().increment(key, -delta);
        if (value != null && value < 0) {
            redisTemplate.opsForValue().set(key, 0);
            return 0L;
        }
        return value;
    }

    @Override
    public void evict(Long userId) {
        redisTemplate.delete(NotificationRedisKeys.unreadCount(userId));
    }
}
