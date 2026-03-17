package com.zhicore.content.application.port.store;

import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.model.OutboxEventRecord;

import java.util.Optional;

/**
 * Outbox 事件存储端口。
 */
public interface OutboxEventStore {

    void save(OutboxEventRecord eventRecord);

    PageResult<OutboxEventRecord> findDead(int page, int size, String eventType);

    Optional<OutboxEventRecord> findByEventId(String eventId);

    void update(OutboxEventRecord eventRecord);
}
