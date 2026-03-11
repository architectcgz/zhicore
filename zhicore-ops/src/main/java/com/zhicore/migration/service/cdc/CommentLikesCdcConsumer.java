package com.zhicore.migration.service.cdc;

import com.zhicore.migration.service.cdc.store.CommentLikesCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 评论点赞 CDC 消费者
 * 监听 comment_likes 表变更，同步更新 Redis 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentLikesCdcConsumer {

    private final CommentLikesCacheStore commentLikesCacheStore;

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
            log.error("处理评论点赞 CDC 事件失败: {}", event, e);
        }
    }

    /**
     * 处理点赞创建
     */
    private void handleCreate(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String commentId = String.valueOf(data.get("comment_id"));
        String userId = String.valueOf(data.get("user_id"));
        commentLikesCacheStore.addLike(commentId, userId);

        log.debug("添加评论点赞缓存: commentId={}, userId={}", commentId, userId);
    }

    /**
     * 处理点赞删除
     */
    private void handleDelete(Map<String, Object> data) {
        if (data == null) {
            return;
        }

        String commentId = String.valueOf(data.get("comment_id"));
        String userId = String.valueOf(data.get("user_id"));
        commentLikesCacheStore.removeLike(commentId, userId);

        log.debug("删除评论点赞缓存: commentId={}, userId={}", commentId, userId);
    }
}
