package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.api.event.comment.CommentDeletedEvent;
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
 * 评论删除热度消费者
 *
 * 消费 CommentDeletedEvent 事件，减少文章热度分数（-10）
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_COMMENT_EVENTS,
        selectorExpression = TopicConstants.TAG_COMMENT_DELETED,
        consumerGroup = RankingConsumerGroups.COMMENT_DELETED_CONSUMER
)
public class CommentDeletedRankingConsumer extends BaseRankingConsumer
        implements RocketMQListener<String> {

    public CommentDeletedRankingConsumer(RankingRedisRepository rankingRepository,
                                         HotScoreCalculator scoreCalculator,
                                         StatefulIdempotentHandler idempotentHandler,
                                         ObjectMapper objectMapper,
                                         ScoreBufferService scoreBufferService) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
    }

    @Override
    public void onMessage(String message) {
        try {
            CommentDeletedEvent event = objectMapper.readValue(message, CommentDeletedEvent.class);
            String messageId = event.getEventId();

            if (!tryProcess(messageId)) {
                log.debug("Message already processed: {}", messageId);
                return;
            }

            try {
                // 减少文章热度分数（负增量，与评论权重对称）
                incrementPostScore(
                        String.valueOf(event.getPostId()),
                        -scoreCalculator.getCommentDelta(),
                        null
                );

                // 同时减少作者的创作者热度
                if (event.getAuthorId() != null) {
                    incrementCreatorScore(
                            String.valueOf(event.getAuthorId()),
                            -scoreCalculator.getCommentDelta()
                    );
                }

                markCompleted(messageId);
                log.info("Processed comment deleted event: postId={}, commentId={}",
                        event.getPostId(), event.getCommentId());
            } catch (Exception e) {
                markFailed(messageId);
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to process comment deleted event: {}", message, e);
            throw new RuntimeException("Failed to process comment deleted event", e);
        }
    }
}
