package com.zhicore.migration.service.cdc;

import com.zhicore.migration.service.cdc.store.CommentStatsCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 评论统计 CDC 消费者
 * 监听 comment_stats 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentStatsCdcConsumer {

    private final CommentStatsCacheStore commentStatsCacheStore;

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
        commentStatsCacheStore.upsert(commentId, data);

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
        commentStatsCacheStore.delete(commentId);

        log.debug("删除评论统计缓存: commentId={}", commentId);
    }
}
