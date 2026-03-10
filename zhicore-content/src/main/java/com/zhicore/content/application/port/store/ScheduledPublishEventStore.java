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

    void update(ScheduledPublishEventRecord eventRecord);

    List<ScheduledPublishEventRecord> findDueScheduledPending(LocalDateTime dbNow, LocalDateTime cooldownBefore, int limit);

    List<ScheduledPublishEventRecord> findStaleScheduledPending(LocalDateTime dbNow, LocalDateTime staleBefore, int limit);

    int casUpdateLastEnqueueAt(ScheduledPublishEventRecord eventRecord, LocalDateTime dbNow, String newEventId);

    Optional<ScheduledPublishEventRecord> findActiveByPostId(Long postId);
}
