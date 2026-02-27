package com.zhicore.ranking.infrastructure.mq;

import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.ranking.application.service.ScoreBufferService;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 排行榜事件消费基类
 *
 * @author ZhiCore Team
 */
@Slf4j
public abstract class BaseRankingConsumer {

    protected final RankingRedisRepository rankingRepository;
    protected final HotScoreCalculator scoreCalculator;
    protected final StatefulIdempotentHandler idempotentHandler;
    protected final ObjectMapper objectMapper;
    protected final ScoreBufferService scoreBufferService;

    protected BaseRankingConsumer(RankingRedisRepository rankingRepository,
                                  HotScoreCalculator scoreCalculator,
                                  StatefulIdempotentHandler idempotentHandler,
                                  ObjectMapper objectMapper,
                                  ScoreBufferService scoreBufferService) {
        this.rankingRepository = rankingRepository;
        this.scoreCalculator = scoreCalculator;
        this.idempotentHandler = idempotentHandler;
        this.objectMapper = objectMapper;
        this.scoreBufferService = scoreBufferService;
    }

    /**
     * 增量更新文章热度分数
     * 使用本地聚合缓冲，定时批量刷写到 Redis
     *
     * <p>增量事件不应用时间衰减，保证正向/负向事件对称（加多少就能减多少）。
     * 时间衰减统一在快照全量重建时应用，与架构文档设计一致（见 02 文档 3.1 节）。</p>
     *
     * @param postId    文章ID
     * @param baseDelta 基础增量（正值为加分，负值为扣分）
     */
    protected void incrementPostScore(String postId, double baseDelta) {
        scoreBufferService.addScore("post", postId, baseDelta);
        log.debug("Buffered post score update: postId={}, delta={}", postId, baseDelta);
    }

    /**
     * 增量更新创作者热度分数
     * 使用本地聚合缓冲，定时批量刷写到 Redis
     *
     * @param userId 用户ID
     * @param delta 增量
     */
    protected void incrementCreatorScore(String userId, double delta) {
        // 使用本地聚合缓冲，而不是直接调用 Redis
        scoreBufferService.addScore("creator", userId, delta);
        log.debug("Buffered creator score update: userId={}, delta={}", userId, delta);
    }

    /**
     * 检查消息是否已处理（幂等性检查）
     *
     * @param messageId 消息ID
     * @return true 如果消息未处理过
     */
    protected boolean tryProcess(String messageId) {
        return idempotentHandler.tryAcquire(messageId);
    }

    /**
     * 标记消息处理完成
     *
     * @param messageId 消息ID
     */
    protected void markCompleted(String messageId) {
        idempotentHandler.markCompleted(messageId);
    }

    /**
     * 标记消息处理失败（释放锁）
     *
     * @param messageId 消息ID
     */
    protected void markFailed(String messageId) {
        idempotentHandler.release(messageId);
    }
}
