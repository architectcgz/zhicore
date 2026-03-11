package com.zhicore.migration.infrastructure.store;

import com.zhicore.migration.service.cdc.store.UserFollowStatsCacheStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisUserFollowStatsCacheStore implements UserFollowStatsCacheStore {

    private static final String USER_FOLLOW_STATS_KEY_PREFIX = "user:follow:stats:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RedissonClient redissonClient;

    @Override
    public void upsert(String userId, Map<String, Object> data) {
        String key = USER_FOLLOW_STATS_KEY_PREFIX + userId;
        redissonClient.<Map<String, Object>>getBucket(key).set(data, CACHE_TTL);
        updateStatField(userId, "following_count", data.get("following_count"));
        updateStatField(userId, "follower_count", data.get("follower_count"));
    }

    @Override
    public void delete(String userId) {
        String key = USER_FOLLOW_STATS_KEY_PREFIX + userId;
        redissonClient.getBucket(key).delete();
        redissonClient.getBucket(key + ":following_count").delete();
        redissonClient.getBucket(key + ":follower_count").delete();
    }

    private void updateStatField(String userId, String field, Object value) {
        if (value == null) {
            return;
        }
        String key = USER_FOLLOW_STATS_KEY_PREFIX + userId + ":" + field;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, CACHE_TTL);
    }
}
