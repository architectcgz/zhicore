package com.zhicore.ranking.infrastructure.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ranking inbox 仓储。
 */
@Repository
public interface RankingEventInboxRepository extends MongoRepository<RankingEventInbox, String> {

    List<RankingEventInbox> findByStatusAndOccurredAtBetweenOrderByOccurredAtAsc(
            RankingEventInbox.InboxStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    long deleteByStatusAndOccurredAtBefore(RankingEventInbox.InboxStatus status, LocalDateTime cutoff);
}
