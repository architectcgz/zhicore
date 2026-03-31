package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.common.monitoring.DurableEventQueueMetrics;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.ScheduledPublishEventMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 定时发布 durable queue 监控。
 */
@Slf4j
@Component
public class ScheduledPublishMetrics {

    private static final String QUEUE_NAME = "content-scheduled-publish";

    private final ScheduledPublishEventMapper scheduledPublishEventMapper;
    private final ScheduledPublishProperties properties;
    private final DurableEventQueueMetrics queueMetrics;

    public ScheduledPublishMetrics(ScheduledPublishEventMapper scheduledPublishEventMapper,
                                   ScheduledPublishProperties properties,
                                   MeterRegistry meterRegistry) {
        this.scheduledPublishEventMapper = scheduledPublishEventMapper;
        this.properties = properties;
        this.queueMetrics = new DurableEventQueueMetrics(meterRegistry, QUEUE_NAME);
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            long pendingCount = scheduledPublishEventMapper.countByStatus(ScheduledPublishEventEntity.ScheduledPublishStatus.PENDING.name());
            OffsetDateTime oldestCreatedAt = scheduledPublishEventMapper.findOldestPendingCreatedAt();
            long oldestAgeSeconds = oldestCreatedAt != null
                    ? Duration.between(oldestCreatedAt, now).getSeconds()
                    : 0L;
            OffsetDateTime since = now.minusMinutes(1);
            long dispatchedLastMinute = scheduledPublishEventMapper.countSucceededSince(since);
            long failedLastMinute = scheduledPublishEventMapper.countFailedSince(since);
            long deadLastMinute = scheduledPublishEventMapper.countDeadSince(since);

            queueMetrics.updateSnapshot(
                    pendingCount,
                    oldestAgeSeconds,
                    dispatchedLastMinute,
                    failedLastMinute,
                    deadLastMinute
            );
            log.debug("Scheduled publish metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min, deadRate={}/min, maxPublishRetries={}",
                    pendingCount, oldestAgeSeconds, dispatchedLastMinute, failedLastMinute, deadLastMinute,
                    properties.getMaxPublishRetries());
        } catch (Exception e) {
            log.error("Failed to collect scheduled publish metrics", e);
        }
    }
}
