package com.zhicore.notification.infrastructure.cache;

import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的通知未读数缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisNotificationUnreadCountStore implements NotificationUnreadCountStore {

    private static final Long APPLIED_RESULT = 1L;

    private static final DefaultRedisScript<Long> INCREMENT_IF_PRESENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('INCRBY', KEYS[1], 1)
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> DECREMENT_IF_PRESENT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            local latest = redis.call('INCRBY', KEYS[1], -tonumber(ARGV[1]))
            if latest <= 0 then
                redis.call('DEL', KEYS[1])
            end
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Integer get(Long userId) {
        Object cached = redisTemplate.opsForValue().get(NotificationRedisKeys.unreadCount(String.valueOf(userId)));
        return cached instanceof Number number ? number.intValue() : null;
    }

    @Override
    public void set(Long userId, int count, Duration ttl) {
        redisTemplate.opsForValue().set(NotificationRedisKeys.unreadCount(String.valueOf(userId)), count, ttl);
    }

    @Override
    public boolean increment(Long userId) {
        String key = NotificationRedisKeys.unreadCount(String.valueOf(userId));
        Long result = redisTemplate.execute(INCREMENT_IF_PRESENT_SCRIPT, List.of(key));
        return APPLIED_RESULT.equals(result);
    }

    @Override
    public boolean decrement(Long userId, int delta) {
        if (delta <= 0) {
            return false;
        }
        String key = NotificationRedisKeys.unreadCount(String.valueOf(userId));
        Long result = redisTemplate.execute(DECREMENT_IF_PRESENT_SCRIPT, List.of(key), delta);
        return APPLIED_RESULT.equals(result);
    }

    @Override
    public void evict(Long userId) {
        redisTemplate.delete(NotificationRedisKeys.unreadCount(String.valueOf(userId)));
    }
}
