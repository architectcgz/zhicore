package com.zhicore.ranking.infrastructure.mongodb;

import com.zhicore.ranking.application.model.RankingInboxEventRecord;
import com.zhicore.ranking.application.port.store.RankingEventInboxStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 基于 MongoDB 的 Ranking inbox 写入实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoRankingEventInboxStore implements RankingEventInboxStore {

    private final RankingEventInboxRepository inboxRepository;

    @Override
    public boolean saveNewEvent(RankingInboxEventRecord event) {
        try {
            inboxRepository.save(RankingEventInbox.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .postId(event.getPostId())
                    .userId(event.getUserId())
                    .authorId(event.getAuthorId())
                    .metricType(event.getMetricType())
                    .countDelta(event.getCountDelta())
                    .scoreDelta(event.getScoreDelta())
                    .occurredAt(event.getOccurredAt())
                    .publishedAt(event.getPublishedAt())
                    .status(RankingEventInbox.InboxStatus.NEW)
                    .retryCount(event.getRetryCount())
                    .createdAt(event.getCreatedAt())
                    .updatedAt(event.getUpdatedAt())
                    .build());
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("ranking inbox 事件已存在，跳过重复写入: eventId={}", event.getEventId());
            return false;
        }
    }
}
