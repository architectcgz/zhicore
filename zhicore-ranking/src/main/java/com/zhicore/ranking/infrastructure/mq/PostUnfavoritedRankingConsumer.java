package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.api.event.post.PostUnfavoritedEvent;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.ranking.application.service.ScoreBufferService;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

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

    public PostUnfavoritedRankingConsumer(RankingRedisRepository rankingRepository,
                                          HotScoreCalculator scoreCalculator,
                                          StatefulIdempotentHandler idempotentHandler,
                                          ObjectMapper objectMapper,
                                          ScoreBufferService scoreBufferService) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostUnfavoritedEvent event = objectMapper.readValue(message, PostUnfavoritedEvent.class);
            String messageId = event.getEventId();

            if (!tryProcess(messageId)) {
                log.debug("Message already processed: {}", messageId);
                return;
            }

            try {
                // 减少文章热度分数（负增量，与收藏权重对称）
                incrementPostScore(
                        String.valueOf(event.getPostId()),
                        -scoreCalculator.getFavoriteDelta(),
                        null
                );

                markCompleted(messageId);
                log.info("Processed post unfavorited event: postId={}, userId={}",
                        event.getPostId(), event.getUserId());
            } catch (Exception e) {
                markFailed(messageId);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process post unfavorited event: {}", message, e);
            throw new RuntimeException("Failed to process post unfavorited event", e);
        }
    }
}
