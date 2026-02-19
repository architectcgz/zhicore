package com.blog.ranking.infrastructure.mq;

import com.blog.api.event.post.PostFavoritedEvent;
import com.blog.common.mq.StatefulIdempotentHandler;
import com.blog.common.mq.TopicConstants;
import com.blog.ranking.application.service.ScoreBufferService;
import com.blog.ranking.domain.service.HotScoreCalculator;
import com.blog.ranking.infrastructure.redis.RankingRedisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文章收藏热度消费者
 * 
 * 消费 PostFavoritedEvent 事件，增量更新文章热度分数
 *
 * @author Blog Team
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

    public PostFavoritedRankingConsumer(RankingRedisRepository rankingRepository,
                                        HotScoreCalculator scoreCalculator,
                                        StatefulIdempotentHandler idempotentHandler,
                                        ObjectMapper objectMapper,
                                        ScoreBufferService scoreBufferService) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostFavoritedEvent event = objectMapper.readValue(message, PostFavoritedEvent.class);
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
                        scoreCalculator.getFavoriteDelta(),
                        null
                );

                // 同时更新作者的创作者热度
                if (event.getAuthorId() != null) {
                    incrementCreatorScore(String.valueOf(event.getAuthorId()), scoreCalculator.getFavoriteDelta());
                }

                markCompleted(messageId);
                log.info("Processed post favorited event: postId={}", event.getPostId());
            } catch (Exception e) {
                markFailed(messageId);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process post favorited event: {}", message, e);
            throw new RuntimeException("Failed to process post favorited event", e);
        }
    }
}
