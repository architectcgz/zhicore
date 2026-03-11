package com.zhicore.migration.service.cdc;

import com.zhicore.migration.service.cdc.store.UserFollowStatsCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户关注统计 CDC 消费者
 * 监听 user_follow_stats 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserFollowStatsCdcConsumer {

    private final UserFollowStatsCacheStore userFollowStatsCacheStore;

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
        userFollowStatsCacheStore.upsert(userId, data);

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
        userFollowStatsCacheStore.delete(userId);

        log.debug("删除用户关注统计缓存: userId={}", userId);
    }
}
