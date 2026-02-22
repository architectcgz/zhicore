package com.zhicore.migration.infrastructure.cdc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 文章统计 CDC 消费者
 * 监听 post_stats 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostStatsCdcConsumer {

    private final RedissonClient redissonClient;

    private static final String POST_STATS_KEY_PREFIX = "post:stats:";
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
            log.error("处理文章统计 CDC 事件失败: {}", event, e);
        }
    }

    /**
     * 处理插入或更新
     */
    private void handleUpsert(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String postId = String.valueOf(data.get("post_id"));
        String key = POST_STATS_KEY_PREFIX + postId;

        // 更新 Redis 缓存
        RBucket<Map<String, Object>> bucket = redissonClient.getBucket(key);
        bucket.set(data, CACHE_TTL);

        // 同时更新各个统计字段的独立缓存
        updateStatField(postId, "view_count", data.get("view_count"));
        updateStatField(postId, "like_count", data.get("like_count"));
        updateStatField(postId, "comment_count", data.get("comment_count"));
        updateStatField(postId, "favorite_count", data.get("favorite_count"));

        log.debug("更新文章统计缓存: postId={}", postId);
    }

    /**
     * 处理删除
     */
    private void handleDelete(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String postId = String.valueOf(data.get("post_id"));
        String key = POST_STATS_KEY_PREFIX + postId;

        // 删除 Redis 缓存
        redissonClient.getBucket(key).delete();

        // 删除各个统计字段的独立缓存
        redissonClient.getBucket(key + ":view_count").delete();
        redissonClient.getBucket(key + ":like_count").delete();
        redissonClient.getBucket(key + ":comment_count").delete();
        redissonClient.getBucket(key + ":favorite_count").delete();

        log.debug("删除文章统计缓存: postId={}", postId);
    }

    /**
     * 更新单个统计字段
     */
    private void updateStatField(String postId, String field, Object value) {
        if (value == null) {
            return;
        }
        String key = POST_STATS_KEY_PREFIX + postId + ":" + field;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, CACHE_TTL);
    }
}
