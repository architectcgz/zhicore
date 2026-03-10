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
        Object cached = redisTemplate.opsForValue().get(NotificationRedisKeys.unreadCount(String.valueOf(userId)));
        return cached instanceof Integer value ? value : null;
    }

    @Override
    public void set(Long userId, int count, Duration ttl) {
        redisTemplate.opsForValue().set(NotificationRedisKeys.unreadCount(String.valueOf(userId)), count, ttl);
    }

    @Override
    public void evict(Long userId) {
        redisTemplate.delete(NotificationRedisKeys.unreadCount(String.valueOf(userId)));
    }
}
