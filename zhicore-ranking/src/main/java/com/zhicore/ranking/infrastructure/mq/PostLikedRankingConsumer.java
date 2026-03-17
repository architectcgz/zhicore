package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.post.PostLikedIntegrationEvent;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 文章点赞热度消费者
 * 
 * 消费 PostLikedEvent 事件，增量更新文章热度分数
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_POST_EVENTS,
        selectorExpression = TopicConstants.TAG_POST_LIKED,
        consumerGroup = RankingConsumerGroups.POST_LIKED_CONSUMER
)
public class PostLikedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public PostLikedRankingConsumer(ObjectMapper objectMapper,
                                    RankingLedgerIngestionService rankingLedgerIngestionService) {
        super(objectMapper, rankingLedgerIngestionService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostLikedIntegrationEvent event = objectMapper.readValue(message, PostLikedIntegrationEvent.class);
            boolean created = saveLedgerEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    event.getUserId(),
                    event.getAuthorId(),
                    RankingMetricType.LIKE,
                    1,
                    LocalDateTime.ofInstant(event.getOccurredAt(), ZoneId.systemDefault()),
                    event.getPublishedAt() != null
                            ? LocalDateTime.ofInstant(event.getPublishedAt(), ZoneId.systemDefault())
                            : null
            );
            if (!created) {
                log.debug("Post liked 事件已写入 ranking ledger，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Post liked 事件已写入 ranking ledger: postId={}, authorId={}",
                    event.getPostId(), event.getAuthorId());
        } catch (Exception e) {
            log.error("Failed to process post liked event: {}", message, e);
            throw new RuntimeException("Failed to process post liked event", e);
        }
    }
}
