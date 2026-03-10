package com.zhicore.user.application.port.store;

import java.time.Duration;

/**
 * 关注统计缓存存储端口。
 *
 * 封装关注数、粉丝数的缓存读写与失效逻辑，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface FollowStatsStore {

    Integer getFollowingCount(Long userId);

    Integer getFollowersCount(Long userId);

    void cacheFollowingCount(Long userId, int count, Duration ttl);

    void cacheFollowersCount(Long userId, int count, Duration ttl);

    void incrementFollowingCount(Long userId);

    void incrementFollowersCount(Long userId);

    void decrementFollowingCount(Long userId);

    void decrementFollowersCount(Long userId);

    void evictStats(Long... userIds);
}
