package com.zhicore.user.infrastructure.cache;

import com.zhicore.user.application.port.store.FollowStatsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Redis 的关注统计缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisFollowStatsStore implements FollowStatsStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Integer getFollowingCount(Long userId) {
        return getCount(UserRedisKeys.followingCount(userId));
    }

    @Override
    public Integer getFollowersCount(Long userId) {
        return getCount(UserRedisKeys.followersCount(userId));
    }

    @Override
    public void cacheFollowingCount(Long userId, int count, Duration ttl) {
        redisTemplate.opsForValue().set(UserRedisKeys.followingCount(userId), count, ttl);
    }

    @Override
    public void cacheFollowersCount(Long userId, int count, Duration ttl) {
        redisTemplate.opsForValue().set(UserRedisKeys.followersCount(userId), count, ttl);
    }

    @Override
    public void incrementFollowingCount(Long userId) {
        redisTemplate.opsForValue().increment(UserRedisKeys.followingCount(userId));
    }

    @Override
    public void incrementFollowersCount(Long userId) {
        redisTemplate.opsForValue().increment(UserRedisKeys.followersCount(userId));
    }

    @Override
    public void decrementFollowingCount(Long userId) {
        redisTemplate.opsForValue().decrement(UserRedisKeys.followingCount(userId));
    }

    @Override
    public void decrementFollowersCount(Long userId) {
        redisTemplate.opsForValue().decrement(UserRedisKeys.followersCount(userId));
    }

    @Override
    public void evictStats(Long... userIds) {
        List<String> keys = new ArrayList<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId == null) {
                    continue;
                }
                keys.add(UserRedisKeys.followingCount(userId));
                keys.add(UserRedisKeys.followersCount(userId));
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Integer getCount(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return null;
        }
        return ((Number) cached).intValue();
    }
}
