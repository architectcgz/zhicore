package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.api.event.post.PostViewedEvent;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.ranking.application.service.ScoreBufferService;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文章浏览热度消费者
 *
 * <p>消费 PostViewedEvent 事件，增量更新文章热度分数。</p>
 * <p>防刷策略：</p>
 * <ul>
 *   <li>同一用户同一文章 30 分钟内只计一次浏览（Redis SET 去重）</li>
 *   <li>单篇文章浏览累计分数上限 5000 分</li>
 * </ul>
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

    /** 单篇文章浏览累计分数上限 */
    private static final double VIEW_SCORE_CAP = 5000.0;

    private final Counter viewDedupCounter;

    public PostViewedRankingConsumer(RankingRedisRepository rankingRepository,
                                     HotScoreCalculator scoreCalculator,
                                     StatefulIdempotentHandler idempotentHandler,
                                     ObjectMapper objectMapper,
                                     ScoreBufferService scoreBufferService,
                                     MeterRegistry meterRegistry) {
        super(rankingRepository, scoreCalculator, idempotentHandler, objectMapper, scoreBufferService);
        this.viewDedupCounter = Counter.builder("ranking.view.dedup")
                .description("浏览去重拦截次数")
                .register(meterRegistry);
    }

    @Override
    public void onMessage(String message) {
        try {
            PostViewedEvent event = objectMapper.readValue(message, PostViewedEvent.class);
            String messageId = event.getEventId();

            // 消息级幂等性检查
            if (!tryProcess(messageId)) {
                log.debug("Message already processed: {}", messageId);
                return;
            }

            try {
                String postId = String.valueOf(event.getPostId());

                // 用户级去重：同一用户同一文章 30 分钟内只计一次
                if (event.getUserId() != null) {
                    String userId = String.valueOf(event.getUserId());
                    if (!rankingRepository.tryAcquireViewDedup(postId, userId)) {
                        viewDedupCounter.increment();
                        log.debug("浏览去重拦截: postId={}, userId={}", postId, userId);
                        markCompleted(messageId);
                        return;
                    }
                }

                // 计算衰减后的分数增量
                double baseDelta = scoreCalculator.getViewDelta();
                double timeDecay = scoreCalculator.calculateTimeDecay(event.getPublishedAt());
                double scoreDelta = baseDelta * timeDecay;

                // 单篇浏览分数上限检查
                double allowedDelta = rankingRepository.incrementViewScoreWithCap(
                        postId, scoreDelta, VIEW_SCORE_CAP);
                if (allowedDelta <= 0) {
                    log.debug("浏览分数已达上限: postId={}, cap={}", postId, VIEW_SCORE_CAP);
                    markCompleted(messageId);
                    return;
                }

                // 使用实际允许的增量写入缓冲区
                scoreBufferService.addScore("post", postId, allowedDelta);

                markCompleted(messageId);
                log.info("Processed post viewed event: postId={}, delta={}", postId, allowedDelta);
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
