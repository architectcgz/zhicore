package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.post.PostFavoritedIntegrationEvent;
import com.zhicore.ranking.application.service.RankingEventInboxService;
import com.zhicore.ranking.domain.model.RankingMetricType;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 文章收藏热度消费者
 * 
 * 消费 PostFavoritedEvent 事件，增量更新文章热度分数
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_POST_EVENTS,
        selectorExpression = TopicConstants.TAG_POST_FAVORITED,
        consumerGroup = RankingConsumerGroups.POST_FAVORITED_CONSUMER
)
public class PostFavoritedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public PostFavoritedRankingConsumer(ObjectMapper objectMapper,
                                        RankingEventInboxService rankingEventInboxService) {
        super(objectMapper, rankingEventInboxService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostFavoritedIntegrationEvent event = objectMapper.readValue(message, PostFavoritedIntegrationEvent.class);
            boolean created = saveInboxEvent(
                    event.getEventId(),
                    event.getClass().getSimpleName(),
                    event.getPostId(),
                    event.getUserId(),
                    event.getAuthorId(),
                    RankingMetricType.FAVORITE,
                    1,
                    LocalDateTime.ofInstant(event.getOccurredAt(), ZoneId.systemDefault()),
                    null
            );
            if (!created) {
                log.debug("Post favorited 事件已写入 ranking inbox，跳过重复消费: eventId={}", event.getEventId());
                return;
            }
            log.info("Post favorited 事件已写入 ranking inbox: postId={}", event.getPostId());
        } catch (Exception e) {
            log.error("Failed to process post favorited event: {}", message, e);
            throw new RuntimeException("Failed to process post favorited event", e);
        }
    }
}
