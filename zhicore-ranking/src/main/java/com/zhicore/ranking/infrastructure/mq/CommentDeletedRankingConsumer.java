package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.api.event.comment.CommentDeletedEvent;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.ranking.application.service.RankingEventInboxService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 评论删除热度消费者
 *
 * 消费 CommentDeletedEvent 事件，减少文章热度分数（-10）
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_COMMENT_EVENTS,
        selectorExpression = TopicConstants.TAG_COMMENT_DELETED,
        consumerGroup = RankingConsumerGroups.COMMENT_DELETED_CONSUMER
)
public class CommentDeletedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public CommentDeletedRankingConsumer(ObjectMapper objectMapper,
                                         RankingEventInboxService rankingEventInboxService) {
        super(objectMapper, rankingEventInboxService);
    }

    @Override
    public void onMessage(String message) {
        try {
            CommentDeletedEvent event = objectMapper.readValue(message, CommentDeletedEvent.class);
            boolean created = saveInboxEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    null,
                    event.getAuthorId(),
                    RankingMetricType.COMMENT,
                    -1,
                    event.getOccurredAt(),
                    null
            );
            if (!created) {
                log.debug("Comment deleted 事件已写入 ranking inbox，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Comment deleted 事件已写入 ranking inbox: postId={}, commentId={}",
                    event.getPostId(), event.getCommentId());
        } catch (Exception e) {
            log.error("Failed to process comment deleted event: {}", message, e);
            throw new RuntimeException("Failed to process comment deleted event", e);
        }
    }
}
