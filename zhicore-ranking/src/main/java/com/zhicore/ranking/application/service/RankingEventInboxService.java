package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInbox;
import com.zhicore.ranking.infrastructure.mongodb.RankingEventInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Ranking 事件 inbox 写入服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingEventInboxService {

    private final RankingEventInboxRepository inboxRepository;
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
        RankingEventInbox entity = RankingEventInbox.builder()
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
                .status(RankingEventInbox.InboxStatus.NEW)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            inboxRepository.save(entity);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("ranking inbox 事件已存在，跳过重复写入: eventId={}", eventId);
            return false;
        }
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
