package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.api.event.post.PostUnlikedEvent;
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

    public PostUnlikedRankingConsumer(RankingRedisRepository rankingRepository,
                                      HotScoreCalculator scoreCalculator,
                                      StatefulIdempotentHandler idempotentHandler,
                                      ObjectMapper objectMapper,
                                      ScoreBufferService scoreBufferService) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostUnlikedEvent event = objectMapper.readValue(message, PostUnlikedEvent.class);
            String messageId = event.getEventId();

            // 幂等性检查
            if (!tryProcess(messageId)) {
                log.debug("Message already processed: {}", messageId);
                return;
            }

            try {
                // 减少文章热度分数（负增量）（事件使用 Long ID，Redis 使用 String）
                incrementPostScore(
                        String.valueOf(event.getPostId()),
                        -scoreCalculator.getLikeDelta()
                );

                // 同时减少作者的创作者热度
                if (event.getAuthorId() != null) {
                    incrementCreatorScore(String.valueOf(event.getAuthorId()), -scoreCalculator.getLikeDelta());
                }

                markCompleted(messageId);
                log.info("Processed post unliked event: postId={}", event.getPostId());
            } catch (Exception e) {
                markFailed(messageId);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process post unliked event: {}", message, e);
            throw new RuntimeException("Failed to process post unliked event", e);
        }
    }
}
