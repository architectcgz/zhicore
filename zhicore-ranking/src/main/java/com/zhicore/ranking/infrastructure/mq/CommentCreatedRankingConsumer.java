package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.api.event.comment.CommentCreatedEvent;
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
 * 评论创建热度消费者
 * 
 * 消费 CommentCreatedEvent 事件，增量更新文章热度分数
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

    public CommentCreatedRankingConsumer(RankingRedisRepository rankingRepository,
                                         HotScoreCalculator scoreCalculator,
                                         StatefulIdempotentHandler idempotentHandler,
                                         ObjectMapper objectMapper,
                                         ScoreBufferService scoreBufferService) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
    }

    @Override
    public void onMessage(String message) {
        try {
            CommentCreatedEvent event = objectMapper.readValue(message, CommentCreatedEvent.class);
            String messageId = event.getEventId();

            // 幂等性检查
            if (!tryProcess(messageId)) {
                log.debug("Message already processed: {}", messageId);
                return;
            }

            try {
                // 增量更新文章热度分数（不应用时间衰减，衰减统一在快照重建时处理）
                incrementPostScore(
                        String.valueOf(event.getPostId()),
                        scoreCalculator.getCommentDelta()
                );

                // 同时更新文章作者的创作者热度
                if (event.getPostOwnerId() != null) {
                    incrementCreatorScore(String.valueOf(event.getPostOwnerId()), scoreCalculator.getCommentDelta());
                }

                markCompleted(messageId);
                log.info("Processed comment created event: postId={}, commentId={}",
                        event.getPostId(), event.getCommentId());
            } catch (Exception e) {
                markFailed(messageId);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process comment created event: {}", message, e);
            throw new RuntimeException("Failed to process comment created event", e);
        }
    }
}
