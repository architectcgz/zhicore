package com.zhicore.content.application.port.repo;

import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定时发布事件仓储端口（R1）
 *
 * 说明：
 * - 当前实现以 scheduled_publish_event 表为主，用于“消费门禁 + 扫描兜底 + 幂等收敛”。
 * - 由于该模型与表结构强绑定，暂直接复用基础设施层 Entity 作为传输对象，避免重复建模。
 */
public interface ScheduledPublishEventRepository {

    LocalDateTime dbNow();

    void save(ScheduledPublishEventEntity entity);

    Optional<ScheduledPublishEventEntity> findByEventId(String eventId);

    void update(ScheduledPublishEventEntity entity);

    List<ScheduledPublishEventEntity> findDueScheduledPending(LocalDateTime dbNow, LocalDateTime cooldownBefore, int limit);

    List<ScheduledPublishEventEntity> findStaleScheduledPending(LocalDateTime dbNow, LocalDateTime staleBefore, int limit);

    int casUpdateLastEnqueueAt(ScheduledPublishEventEntity event, LocalDateTime dbNow, String newEventId);

    Optional<ScheduledPublishEventEntity> findActiveByPostId(Long postId);
}
