package com.zhicore.migration.infrastructure.store;

import com.zhicore.migration.service.cdc.store.CommentStatsCacheStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisCommentStatsCacheStore implements CommentStatsCacheStore {

    private static final String COMMENT_STATS_KEY_PREFIX = "comment:stats:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RedissonClient redissonClient;

    @Override
    public void upsert(String commentId, Map<String, Object> data) {
        String key = COMMENT_STATS_KEY_PREFIX + commentId;
        redissonClient.<Map<String, Object>>getBucket(key).set(data, CACHE_TTL);
        updateStatField(commentId, "like_count", data.get("like_count"));
        updateStatField(commentId, "reply_count", data.get("reply_count"));
        updateStatField(commentId, "hot_score", data.get("hot_score"));
    }

    @Override
    public void delete(String commentId) {
        String key = COMMENT_STATS_KEY_PREFIX + commentId;
        redissonClient.getBucket(key).delete();
        redissonClient.getBucket(key + ":like_count").delete();
        redissonClient.getBucket(key + ":reply_count").delete();
        redissonClient.getBucket(key + ":hot_score").delete();
    }

    private void updateStatField(String commentId, String field, Object value) {
        if (value == null) {
            return;
        }
        String key = COMMENT_STATS_KEY_PREFIX + commentId + ":" + field;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, CACHE_TTL);
    }
}
