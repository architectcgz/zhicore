package com.blog.migration.infrastructure.cdc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 用户关注统计 CDC 消费者
 * 监听 user_follow_stats 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserFollowStatsCdcConsumer {

    private final RedissonClient redissonClient;

    private static final String USER_FOLLOW_STATS_KEY_PREFIX = "user:follow:stats:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 消费 CDC 事件
     */
    public void consume(CdcEvent event) {
        try {
            switch (event.getOperation()) {
                case CREATE, UPDATE -> handleUpsert(event.getAfter());
                case DELETE -> handleDelete(event.getBefore());
                default -> log.debug("忽略操作类型: {}", event.getOperation());
            }
        } catch (Exception e) {
            log.error("处理用户关注统计 CDC 事件失败: {}", event, e);
        }
    }

    /**
     * 处理插入或更新
     */
    private void handleUpsert(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String userId = String.valueOf(data.get("user_id"));
        String key = USER_FOLLOW_STATS_KEY_PREFIX + userId;

        // 更新 Redis 缓存
        RBucket<Map<String, Object>> bucket = redissonClient.getBucket(key);
        bucket.set(data, CACHE_TTL);

        // 同时更新各个统计字段的独立缓存
        updateStatField(userId, "following_count", data.get("following_count"));
        updateStatField(userId, "follower_count", data.get("follower_count"));

        log.debug("更新用户关注统计缓存: userId={}", userId);
    }

    /**
     * 处理删除
     */
    private void handleDelete(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String userId = String.valueOf(data.get("user_id"));
        String key = USER_FOLLOW_STATS_KEY_PREFIX + userId;

        // 删除 Redis 缓存
        redissonClient.getBucket(key).delete();

        // 删除各个统计字段的独立缓存
        redissonClient.getBucket(key + ":following_count").delete();
        redissonClient.getBucket(key + ":follower_count").delete();

        log.debug("删除用户关注统计缓存: userId={}", userId);
    }

    /**
     * 更新单个统计字段
     */
    private void updateStatField(String userId, String field, Object value) {
        if (value == null) {
            return;
        }
        String key = USER_FOLLOW_STATS_KEY_PREFIX + userId + ":" + field;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, CACHE_TTL);
    }
}
