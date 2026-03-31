package com.zhicore.ranking.application.service;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.ServiceUnavailableException;
import com.zhicore.ranking.application.model.RankingLedgerEventRecord;
import com.zhicore.ranking.domain.model.RankingMetricType;
import com.zhicore.ranking.infrastructure.config.RankingPipelineProperties;
import com.zhicore.ranking.infrastructure.pg.PgRankingLedgerRepository;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Ranking 事件落账服务。
 */
@Service
@RequiredArgsConstructor
public class RankingLedgerIngestionService {

    private final PgRankingLedgerRepository repository;
    private final RankingPipelineProperties pipelineProperties;
    private final LockManager lockManager;

    @Transactional
    public boolean saveEvent(String eventId,
                             String eventType,
                             Long postId,
                             Long userId,
                             Long authorId,
                             RankingMetricType metricType,
                             int countDelta,
                             OffsetDateTime occurredAt,
                             OffsetDateTime publishedAt) {
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

        repository.acquireReplayBarrierSharedLock();
        if (lockManager.isLocked(RankingRedisKeys.replayLock())) {
            throw new ServiceUnavailableException("排行榜正在执行 ledger 补算，请稍后重试");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime actualOccurredAt = occurredAt != null ? occurredAt : now;
        RankingLedgerEventRecord eventRecord = RankingLedgerEventRecord.builder()
                .eventId(eventId)
                .eventType(eventType)
                .postId(postId)
                .actorId(userId)
                .authorId(authorId)
                .metricType(metricType)
                .delta(countDelta)
                .occurredAt(actualOccurredAt)
                .publishedAt(publishedAt)
                .partitionKey(String.valueOf(postId))
                .sourceService(resolveSourceService(eventType))
                .sourceOpId(eventId)
                .createdAt(now)
                .build();
        return repository.saveEventAndAccumulateBucket(
                eventRecord,
                floorBucketStart(actualOccurredAt)
        );
    }

    private String resolveSourceService(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "unknown";
        }
        int idx = eventType.indexOf("IntegrationEvent");
        return idx > 0 ? eventType.substring(0, idx) : eventType;
    }

    private OffsetDateTime floorBucketStart(OffsetDateTime occurredAt) {
        long bucketWindowSeconds = pipelineProperties.getBucketWindowSeconds();
        long epochSecond = occurredAt.toEpochSecond();
        long floored = (epochSecond / bucketWindowSeconds) * bucketWindowSeconds;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(floored), ZoneId.systemDefault());
    }
}
