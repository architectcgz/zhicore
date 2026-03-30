package com.zhicore.comment.domain.repository;

import com.zhicore.comment.domain.model.OutboxEvent;
import com.zhicore.comment.domain.model.OutboxEventStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 评论服务 outbox 仓储。
 */
public interface OutboxEventRepository {

    void save(OutboxEvent event);

    void update(OutboxEvent event);

    Optional<OutboxEvent> findById(String id);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, int limit);

    List<OutboxEvent> findByStatus(OutboxEventStatus status);

    long countByStatus(OutboxEventStatus status);

    OffsetDateTime findOldestPendingCreatedAt();

    long countSucceededSince(OffsetDateTime since);

    long countFailedSince(OffsetDateTime since, int defaultMaxRetries);

    long countDeadSince(OffsetDateTime since, int defaultMaxRetries);

    List<OutboxEvent> claimRetryableEvents(OffsetDateTime now,
                                           OffsetDateTime reclaimBefore,
                                           String claimedBy,
                                           int limit);
}
