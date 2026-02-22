package com.zhicore.migration.infrastructure.cdc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 评论统计 CDC 消费者
 * 监听 comment_stats 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentStatsCdcConsumer {

    private final RedissonClient redissonClient;

    private static final String COMMENT_STATS_KEY_PREFIX = "comment:stats:";
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
            log.error("处理评论统计 CDC 事件失败: {}", event, e);
        }
    }

    /**
     * 处理插入或更新
     */
    private void handleUpsert(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String commentId = String.valueOf(data.get("comment_id"));
        String key = COMMENT_STATS_KEY_PREFIX + commentId;

        // 更新 Redis 缓存
        RBucket<Map<String, Object>> bucket = redissonClient.getBucket(key);
        bucket.set(data, CACHE_TTL);

        // 同时更新各个统计字段的独立缓存
        updateStatField(commentId, "like_count", data.get("like_count"));
        updateStatField(commentId, "reply_count", data.get("reply_count"));
        updateStatField(commentId, "hot_score", data.get("hot_score"));

        log.debug("更新评论统计缓存: commentId={}", commentId);
    }

    /**
     * 处理删除
     */
    private void handleDelete(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String commentId = String.valueOf(data.get("comment_id"));
        String key = COMMENT_STATS_KEY_PREFIX + commentId;

        // 删除 Redis 缓存
        redissonClient.getBucket(key).delete();

        // 删除各个统计字段的独立缓存
        redissonClient.getBucket(key + ":like_count").delete();
        redissonClient.getBucket(key + ":reply_count").delete();
        redissonClient.getBucket(key + ":hot_score").delete();

        log.debug("删除评论统计缓存: commentId={}", commentId);
    }

    /**
     * 更新单个统计字段
     */
    private void updateStatField(String commentId, String field, Object value) {
        if (value == null) {
            return;
        }
        String key = COMMENT_STATS_KEY_PREFIX + commentId + ":" + field;
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, CACHE_TTL);
    }
}
