package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.post.PostUnfavoritedIntegrationEvent;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 文章取消收藏热度消费者
 *
 * 消费 PostUnfavoritedEvent 事件，减少文章热度分数（-8）
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_POST_EVENTS,
        selectorExpression = TopicConstants.TAG_POST_UNFAVORITED,
        consumerGroup = RankingConsumerGroups.POST_UNFAVORITED_CONSUMER
)
public class PostUnfavoritedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public PostUnfavoritedRankingConsumer(ObjectMapper objectMapper,
                                          RankingLedgerIngestionService rankingLedgerIngestionService) {
        super(objectMapper, rankingLedgerIngestionService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostUnfavoritedIntegrationEvent event = objectMapper.readValue(message, PostUnfavoritedIntegrationEvent.class);
            boolean created = saveLedgerEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    event.getUserId(),
                    null,
                    RankingMetricType.FAVORITE,
                    -1,
                    LocalDateTime.ofInstant(event.getOccurredAt(), ZoneId.systemDefault()),
                    null
            );
            if (!created) {
                log.debug("Post unfavorited 事件已写入 ranking ledger，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Post unfavorited 事件已写入 ranking ledger: postId={}, userId={}",
                    event.getPostId(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process post unfavorited event: {}", message, e);
            throw new RuntimeException("Failed to process post unfavorited event", e);
        }
    }
}
