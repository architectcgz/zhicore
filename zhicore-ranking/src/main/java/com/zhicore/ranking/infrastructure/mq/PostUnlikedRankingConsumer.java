package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.post.PostUnlikedIntegrationEvent;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 文章取消点赞热度消费者
 * 
 * 消费 PostUnlikedEvent 事件，减少文章热度分数
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_POST_EVENTS,
        selectorExpression = TopicConstants.TAG_POST_UNLIKED,
        consumerGroup = RankingConsumerGroups.POST_UNLIKED_CONSUMER
)
public class PostUnlikedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public PostUnlikedRankingConsumer(ObjectMapper objectMapper,
                                      RankingLedgerIngestionService rankingLedgerIngestionService) {
        super(objectMapper, rankingLedgerIngestionService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostUnlikedIntegrationEvent event = objectMapper.readValue(message, PostUnlikedIntegrationEvent.class);
            boolean created = saveLedgerEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    event.getUserId(),
                    event.getAuthorId(),
                    RankingMetricType.LIKE,
                    -1,
                    LocalDateTime.ofInstant(event.getOccurredAt(), ZoneId.systemDefault()),
                    null
            );
            if (!created) {
                log.debug("Post unliked 事件已写入 ranking ledger，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Post unliked 事件已写入 ranking ledger: postId={}", event.getPostId());
        } catch (Exception e) {
            log.error("Failed to process post unliked event: {}", message, e);
            throw new RuntimeException("Failed to process post unliked event", e);
        }
    }
}
