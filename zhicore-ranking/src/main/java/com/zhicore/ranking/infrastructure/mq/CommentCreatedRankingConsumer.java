package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.comment.CommentCreatedIntegrationEvent;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 评论创建热度消费者
 * 
 * 消费 CommentCreatedIntegrationEvent 事件，增量更新文章热度分数
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_COMMENT_EVENTS,
        selectorExpression = TopicConstants.TAG_COMMENT_CREATED,
        consumerGroup = RankingConsumerGroups.COMMENT_CREATED_CONSUMER
)
public class CommentCreatedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public CommentCreatedRankingConsumer(ObjectMapper objectMapper,
                                         RankingLedgerIngestionService rankingLedgerIngestionService) {
        super(objectMapper, rankingLedgerIngestionService);
    }

    @Override
    public void onMessage(String message) {
        try {
            CommentCreatedIntegrationEvent event = objectMapper.readValue(message, CommentCreatedIntegrationEvent.class);
            boolean created = saveLedgerEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    event.getCommentAuthorId(),
                    event.getPostOwnerId(),
                    RankingMetricType.COMMENT,
                    1,
                    LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC),
                    null
            );
            if (!created) {
                log.debug("Comment created 事件已写入 ranking ledger，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Comment created 事件已写入 ranking ledger: postId={}, commentId={}",
                    event.getPostId(), event.getCommentId());
        } catch (Exception e) {
            log.error("Failed to process comment created event: {}", message, e);
            throw new RuntimeException("Failed to process comment created event", e);
        }
    }
}
