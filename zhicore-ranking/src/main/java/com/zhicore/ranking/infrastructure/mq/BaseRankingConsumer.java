package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.domain.model.RankingMetricType;

import java.time.LocalDateTime;

/**
 * 排行榜事件消费基类。
 */
public abstract class BaseRankingConsumer {

    protected final ObjectMapper objectMapper;
    protected final RankingLedgerIngestionService rankingLedgerIngestionService;

    protected BaseRankingConsumer(ObjectMapper objectMapper,
                                  RankingLedgerIngestionService rankingLedgerIngestionService) {
        this.objectMapper = objectMapper;
        this.rankingLedgerIngestionService = rankingLedgerIngestionService;
    }

    protected boolean saveLedgerEvent(String eventId,
                                      String eventType,
                                      Long postId,
                                      Long userId,
                                      Long authorId,
                                      RankingMetricType metricType,
                                      int countDelta,
                                      LocalDateTime occurredAt,
                                      LocalDateTime publishedAt) {
        return rankingLedgerIngestionService.saveEvent(
                eventId,
                eventType,
                postId,
                userId,
                authorId,
                metricType,
                countDelta,
                occurredAt,
                publishedAt
        );
    }
}
