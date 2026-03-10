package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.RankingInboxEventRecord;
import com.zhicore.ranking.application.port.store.RankingEventInboxStore;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Ranking 事件 inbox 写入服务。
 */
@Service
@RequiredArgsConstructor
public class RankingEventInboxService {

    private final RankingEventInboxStore rankingEventInboxStore;
    private final HotScoreCalculator hotScoreCalculator;

    public boolean saveEvent(String eventId,
                             String eventType,
                             Long postId,
                             Long userId,
                             Long authorId,
                             RankingMetricType metricType,
                             int countDelta,
                             LocalDateTime occurredAt,
                             LocalDateTime publishedAt) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        if (postId == null) {
            throw new IllegalArgumentException("postId 不能为空");
        }
        if (metricType == null) {
            throw new IllegalArgumentException("metricType 不能为空");
        }
        if (countDelta == 0) {
            throw new IllegalArgumentException("countDelta 不能为 0");
        }

        LocalDateTime now = LocalDateTime.now();
        RankingInboxEventRecord eventRecord = RankingInboxEventRecord.builder()
                .eventId(eventId)
                .eventType(eventType)
                .postId(postId)
                .userId(userId)
                .authorId(authorId)
                .metricType(metricType)
                .countDelta(countDelta)
                .scoreDelta(resolveUnitScore(metricType) * countDelta)
                .occurredAt(occurredAt != null ? occurredAt : now)
                .publishedAt(publishedAt)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return rankingEventInboxStore.saveNewEvent(eventRecord);
    }

    private double resolveUnitScore(RankingMetricType metricType) {
        return switch (metricType) {
            case VIEW -> hotScoreCalculator.getViewDelta();
            case LIKE -> hotScoreCalculator.getLikeDelta();
            case FAVORITE -> hotScoreCalculator.getFavoriteDelta();
            case COMMENT -> hotScoreCalculator.getCommentDelta();
        };
    }
}
