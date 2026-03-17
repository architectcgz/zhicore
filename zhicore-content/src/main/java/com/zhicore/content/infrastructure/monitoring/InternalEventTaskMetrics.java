package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.common.monitoring.DurableEventQueueMetrics;
import com.zhicore.content.infrastructure.config.InternalEventDispatcherProperties;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.InternalEventTaskMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * content 内部事件任务监控。
 */
@Slf4j
@Component
public class InternalEventTaskMetrics {

    private static final String QUEUE_NAME = "content-internal-event";

    private final InternalEventTaskMapper internalEventTaskMapper;
    private final InternalEventDispatcherProperties properties;
    private final DurableEventQueueMetrics queueMetrics;

    public InternalEventTaskMetrics(InternalEventTaskMapper internalEventTaskMapper,
                                    InternalEventDispatcherProperties properties,
                                    MeterRegistry meterRegistry) {
        this.internalEventTaskMapper = internalEventTaskMapper;
        this.properties = properties;
        this.queueMetrics = new DurableEventQueueMetrics(meterRegistry, QUEUE_NAME);
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            long pendingCount = internalEventTaskMapper.countByStatus(InternalEventTaskEntity.InternalEventTaskStatus.PENDING.name());
            Instant oldestCreatedAt = internalEventTaskMapper.findOldestPendingCreatedAt();
            long oldestAgeSeconds = oldestCreatedAt != null
                    ? Duration.between(oldestCreatedAt, Instant.now()).getSeconds()
                    : 0L;
            Instant since = Instant.now().minus(Duration.ofMinutes(1));
            long dispatchedLastMinute = internalEventTaskMapper.countSucceededSince(since);
            long failedLastMinute = internalEventTaskMapper.countFailedSince(since, properties.getMaxRetry());
            long deadLastMinute = internalEventTaskMapper.countDeadSince(since, properties.getMaxRetry());

            queueMetrics.updateSnapshot(
                    pendingCount,
                    oldestAgeSeconds,
                    dispatchedLastMinute,
                    failedLastMinute,
                    deadLastMinute
            );
            log.debug("Internal event metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min, deadRate={}/min",
                    pendingCount, oldestAgeSeconds, dispatchedLastMinute, failedLastMinute, deadLastMinute);
        } catch (Exception e) {
            log.error("Failed to collect internal event metrics", e);
        }
    }
}
