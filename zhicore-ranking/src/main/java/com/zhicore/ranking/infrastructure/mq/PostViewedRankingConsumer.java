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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文章浏览热度消费者
 *
 * <p>消费 PostViewedEvent 事件，增量更新文章热度分数。</p>
 * <p>防刷策略：</p>
 * <ul>
 *   <li>登录用户：同一用户同一文章 30 分钟内只计一次浏览（Redis SET 去重）</li>
 *   <li>匿名用户：按 IP+UserAgent 指纹去重，同一指纹同一文章 30 分钟内只计一次</li>
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

                // 浏览去重：登录用户按 userId，匿名用户按 IP+UA 指纹
                String dedupId = resolveDedupId(event);
                if (dedupId != null && !rankingRepository.tryAcquireViewDedup(postId, dedupId)) {
                    viewDedupCounter.increment();
                    log.debug("浏览去重拦截: postId={}, dedupId={}", postId, dedupId);
                    markCompleted(messageId);
                    return;
                }

                // 使用基础权重（不应用时间衰减，衰减统一在快照重建时处理）
                double scoreDelta = scoreCalculator.getViewDelta();

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

    /**
     * 解析去重标识：登录用户用 userId，匿名用户用 IP+UA 的 SHA-256 指纹
     *
     * @return 去重 ID，无法生成时返回 null（跳过去重）
     */
    private String resolveDedupId(PostViewedEvent event) {
        if (event.getUserId() != null) {
            return String.valueOf(event.getUserId());
        }
        // 匿名用户：IP + UserAgent 生成指纹
        String ip = event.getClientIp();
        String ua = event.getUserAgent();
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        String raw = ip + "|" + (ua != null ? ua : "");
        return "anon:" + sha256Short(raw);
    }

    /**
     * SHA-256 取前 16 位十六进制，足够去重使用
     */
    private static String sha256Short(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 guaranteed by JDK
            throw new RuntimeException(e);
        }
    }
}
