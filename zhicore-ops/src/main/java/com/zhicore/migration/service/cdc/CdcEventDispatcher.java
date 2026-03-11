package com.zhicore.migration.service.cdc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CDC 业务事件分发器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdcEventDispatcher {

    private final PostStatsCdcConsumer postStatsCdcConsumer;
    private final PostLikesCdcConsumer postLikesCdcConsumer;
    private final CommentStatsCdcConsumer commentStatsCdcConsumer;
    private final CommentLikesCdcConsumer commentLikesCdcConsumer;
    private final UserFollowStatsCdcConsumer userFollowStatsCdcConsumer;

    /**
     * 分发事件到对应的业务消费者。
     */
    public void dispatch(CdcEvent event) {
        switch (event.getTable()) {
            case "post_stats" -> postStatsCdcConsumer.consume(event);
            case "post_likes" -> postLikesCdcConsumer.consume(event);
            case "comment_stats" -> commentStatsCdcConsumer.consume(event);
            case "comment_likes" -> commentLikesCdcConsumer.consume(event);
            case "user_follow_stats" -> userFollowStatsCdcConsumer.consume(event);
            default -> log.warn("未知的表: {}", event.getTable());
        }
    }
}
