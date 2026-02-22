package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.api.event.post.PostViewedEvent;
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
 * 文章浏览热度消费者
 * 
 * 消费 PostViewedEvent 事件，增量更新文章热度分数
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_POST_EVENTS,
        selectorExpression = TopicConstants.TAG_POST_VIEWED,
        consumerGroup = RankingConsumerGroups.POST_VIEWED_CONSUMER
)
public class PostViewedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public PostViewedRankingConsumer(RankingRedisRepository rankingRepository,
                                     HotScoreCalculator scoreCalculator,
                                     StatefulIdempotentHandler idempotentHandler,
                                     ObjectMapper objectMapper,
                                     ScoreBufferService scoreBufferService) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostViewedEvent event = objectMapper.readValue(message, PostViewedEvent.class);
            String messageId = event.getEventId();

            // 幂等性检查
            if (!tryProcess(messageId)) {
                log.debug("Message already processed: {}", messageId);
                return;
            }

            try {
                // 增量更新文章热度分数（事件使用 Long ID，Redis 使用 String）
                incrementPostScore(
                        String.valueOf(event.getPostId()),
                        scoreCalculator.getViewDelta(),
                        event.getPublishedAt()
                );

                markCompleted(messageId);
                log.info("Processed post viewed event: postId={}", event.getPostId());
            } catch (Exception e) {
                markFailed(messageId);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process post viewed event: {}", message, e);
            throw new RuntimeException("Failed to process post viewed event", e);
        }
    }
}
