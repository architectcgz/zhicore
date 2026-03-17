package com.zhicore.comment.domain.repository;

import com.zhicore.comment.domain.model.OutboxEvent;
import com.zhicore.comment.domain.model.OutboxEventStatus;

import java.time.LocalDateTime;
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

    LocalDateTime findOldestPendingCreatedAt();

    long countSucceededSince(LocalDateTime since);

    long countFailedSince(LocalDateTime since, int defaultMaxRetries);

    long countDeadSince(LocalDateTime since, int defaultMaxRetries);

    List<OutboxEvent> claimRetryableEvents(LocalDateTime now,
                                           LocalDateTime reclaimBefore,
                                           String claimedBy,
                                           int limit);
}
