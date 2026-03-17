package com.zhicore.content.application.port.store;

import com.zhicore.content.application.model.ScheduledPublishEventRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定时发布事件存储端口（R1）。
 */
public interface ScheduledPublishEventStore {

    LocalDateTime dbNow();

    void save(ScheduledPublishEventRecord eventRecord);

    Optional<ScheduledPublishEventRecord> findByEventId(String eventId);

    Optional<ScheduledPublishEventRecord> findByTriggerEventId(String triggerEventId);

    void update(ScheduledPublishEventRecord eventRecord);

    List<ScheduledPublishEventRecord> claimCompensationBatch(
            LocalDateTime dbNow,
            LocalDateTime reclaimBefore,
            String claimedBy,
            int limit
    );

    Optional<ScheduledPublishEventRecord> claimForConsumption(
            String eventId,
            LocalDateTime dbNow,
            LocalDateTime reclaimBefore,
            String claimedBy
    );

    Optional<ScheduledPublishEventRecord> findActiveByPostId(Long postId);

    int markTerminalByPostId(
            Long postId,
            ScheduledPublishEventRecord.ScheduledPublishStatus status,
            LocalDateTime dbNow,
            String lastError
    );
}
