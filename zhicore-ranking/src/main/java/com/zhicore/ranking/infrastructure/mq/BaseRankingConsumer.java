package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.ranking.application.service.RankingEventInboxService;
import com.zhicore.ranking.domain.model.RankingMetricType;

import java.time.LocalDateTime;

/**
 * 排行榜事件消费基类。
 */
public abstract class BaseRankingConsumer {

    protected final ObjectMapper objectMapper;
    protected final RankingEventInboxService rankingEventInboxService;

    protected BaseRankingConsumer(ObjectMapper objectMapper,
                                  RankingEventInboxService rankingEventInboxService) {
        this.objectMapper = objectMapper;
        this.rankingEventInboxService = rankingEventInboxService;
    }

    protected boolean saveInboxEvent(String eventId,
                                     String eventType,
                                     Long postId,
                                     Long userId,
                                     Long authorId,
                                     RankingMetricType metricType,
                                     int countDelta,
                                     LocalDateTime occurredAt,
                                     LocalDateTime publishedAt) {
        return rankingEventInboxService.saveEvent(
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
