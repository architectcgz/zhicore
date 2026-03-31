package com.zhicore.user.infrastructure.monitoring;

import com.zhicore.common.monitoring.DurableEventQueueMetrics;
import com.zhicore.user.domain.model.OutboxEventStatus;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.infrastructure.config.UserOutboxProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 用户服务 outbox 观测指标。
 */
@Slf4j
@Component
public class UserOutboxMetrics {

    private static final String QUEUE_NAME = "user-outbox";

    private final OutboxEventRepository outboxEventRepository;
    private final UserOutboxProperties properties;
    private final DurableEventQueueMetrics queueMetrics;

    public UserOutboxMetrics(OutboxEventRepository outboxEventRepository,
                             UserOutboxProperties properties,
                             MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.queueMetrics = new DurableEventQueueMetrics(meterRegistry, QUEUE_NAME);
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            long pendingCount = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
            OffsetDateTime oldestCreatedAt = outboxEventRepository.findOldestPendingCreatedAt();
            long oldestAgeSeconds = oldestCreatedAt != null ? Duration.between(oldestCreatedAt, now).getSeconds() : 0L;
            OffsetDateTime since = now.minusMinutes(1);
            long dispatchedLastMinute = outboxEventRepository.countSucceededSince(since);
            long failedLastMinute = outboxEventRepository.countFailedSince(since, properties.getMaxRetry());
            long deadLastMinute = outboxEventRepository.countDeadSince(since, properties.getMaxRetry());

            queueMetrics.updateSnapshot(
                    pendingCount,
                    oldestAgeSeconds,
                    dispatchedLastMinute,
                    failedLastMinute,
                    deadLastMinute
            );
            if (pendingCount > 0 || dispatchedLastMinute > 0 || failedLastMinute > 0 || deadLastMinute > 0) {
                log.debug("User outbox metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min, deadRate={}/min",
                        pendingCount, oldestAgeSeconds, dispatchedLastMinute, failedLastMinute, deadLastMinute);
            } else {
                log.trace("User outbox idle snapshot: pending=0, dispatchRate=0/min, failureRate=0/min, deadRate=0/min");
            }
        } catch (Exception e) {
            log.error("Failed to collect user outbox metrics", e);
        }
    }
}
