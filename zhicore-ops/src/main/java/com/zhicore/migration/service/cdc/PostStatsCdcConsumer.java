package com.zhicore.migration.service.cdc;

import com.zhicore.migration.service.cdc.store.PostStatsCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文章统计 CDC 消费者
 * 监听 post_stats 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostStatsCdcConsumer {

    private final PostStatsCacheStore postStatsCacheStore;

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
        postStatsCacheStore.upsert(postId, data);

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
        postStatsCacheStore.delete(postId);

        log.debug("删除文章统计缓存: postId={}", postId);
    }
}
