package com.zhicore.migration.infrastructure.cdc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文章点赞 CDC 消费者
 * 监听 post_likes 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostLikesCdcConsumer {

    private final RedissonClient redissonClient;

    private static final String POST_LIKES_SET_PREFIX = "post:likes:";
    private static final String POST_LIKE_COUNT_PREFIX = "post:stats:";

    /**
     * 消费 CDC 事件
     */
    public void consume(CdcEvent event) {
        try {
            switch (event.getOperation()) {
                case CREATE -> handleCreate(event.getAfter());
                case DELETE -> handleDelete(event.getBefore());
                default -> log.debug("忽略操作类型: {}", event.getOperation());
            }
        } catch (Exception e) {
            log.error("处理文章点赞 CDC 事件失败: {}", event, e);
        }
    }

    /**
     * 处理点赞创建
     */
    private void handleCreate(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String postId = String.valueOf(data.get("post_id"));
        String userId = String.valueOf(data.get("user_id"));

        // 添加到点赞集合
        String setKey = POST_LIKES_SET_PREFIX + postId;
        RSet<String> likeSet = redissonClient.getSet(setKey);
        likeSet.add(userId);

        // 增加点赞计数
        String countKey = POST_LIKE_COUNT_PREFIX + postId + ":like_count";
        RAtomicLong counter = redissonClient.getAtomicLong(countKey);
        counter.incrementAndGet();

        log.debug("添加文章点赞缓存: postId={}, userId={}", postId, userId);
    }

    /**
     * 处理点赞删除
     */
    private void handleDelete(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String postId = String.valueOf(data.get("post_id"));
        String userId = String.valueOf(data.get("user_id"));

        // 从点赞集合移除
        String setKey = POST_LIKES_SET_PREFIX + postId;
        RSet<String> likeSet = redissonClient.getSet(setKey);
        likeSet.remove(userId);

        // 减少点赞计数
        String countKey = POST_LIKE_COUNT_PREFIX + postId + ":like_count";
        RAtomicLong counter = redissonClient.getAtomicLong(countKey);
        long newCount = counter.decrementAndGet();
        if (newCount < 0) {
            counter.set(0);
        }

        log.debug("删除文章点赞缓存: postId={}, userId={}", postId, userId);
    }
}
