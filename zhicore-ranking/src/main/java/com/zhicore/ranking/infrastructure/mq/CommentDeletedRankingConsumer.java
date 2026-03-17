package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.comment.CommentDeletedIntegrationEvent;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 评论删除热度消费者
 *
 * 消费 CommentDeletedIntegrationEvent 事件，减少文章热度分数（-10）
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
                                         RankingLedgerIngestionService rankingLedgerIngestionService) {
        super(objectMapper, rankingLedgerIngestionService);
    }

    @Override
    public void onMessage(String message) {
        try {
            CommentDeletedIntegrationEvent event = objectMapper.readValue(message, CommentDeletedIntegrationEvent.class);
            boolean created = saveLedgerEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    null,
                    event.getAuthorId(),
                    RankingMetricType.COMMENT,
                    -1,
                    LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC),
                    null
            );
            if (!created) {
                log.debug("Comment deleted 事件已写入 ranking ledger，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Comment deleted 事件已写入 ranking ledger: postId={}, commentId={}",
                    event.getPostId(), event.getCommentId());
        } catch (Exception e) {
            log.error("Failed to process comment deleted event: {}", message, e);
            throw new RuntimeException("Failed to process comment deleted event", e);
        }
    }
}
