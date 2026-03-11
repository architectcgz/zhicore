package com.zhicore.migration.infrastructure.store;

import com.zhicore.migration.service.cdc.store.PostStatsCacheStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisPostStatsCacheStore implements PostStatsCacheStore {

    private static final String POST_STATS_KEY_PREFIX = "post:stats:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RedissonClient redissonClient;

    @Override
    public void upsert(String postId, Map<String, Object> data) {
        String key = POST_STATS_KEY_PREFIX + postId;
        redissonClient.<Map<String, Object>>getBucket(key).set(data, CACHE_TTL);
        updateStatField(postId, "view_count", data.get("view_count"));
        updateStatField(postId, "like_count", data.get("like_count"));
        updateStatField(postId, "comment_count", data.get("comment_count"));
        updateStatField(postId, "favorite_count", data.get("favorite_count"));
    }

    @Override
    public void delete(String postId) {
        String key = POST_STATS_KEY_PREFIX + postId;
        redissonClient.getBucket(key).delete();
        redissonClient.getBucket(key + ":view_count").delete();
        redissonClient.getBucket(key + ":like_count").delete();
        redissonClient.getBucket(key + ":comment_count").delete();
        redissonClient.getBucket(key + ":favorite_count").delete();
    }

    private void updateStatField(String postId, String field, Object value) {
        if (value == null) {
            return;
        }
        String key = POST_STATS_KEY_PREFIX + postId + ":" + field;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, CACHE_TTL);
    }
}
