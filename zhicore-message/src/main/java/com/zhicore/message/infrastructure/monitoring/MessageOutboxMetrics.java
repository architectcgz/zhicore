package com.zhicore.message.infrastructure.monitoring;

import com.zhicore.common.monitoring.DurableEventQueueMetrics;
import com.zhicore.message.infrastructure.config.MessageOutboxProperties;
import com.zhicore.message.infrastructure.mq.MessageOutboxTaskStatus;
import com.zhicore.message.infrastructure.repository.mapper.MessageOutboxTaskMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 消息服务 outbox 观测指标。
 */
@Slf4j
@Component
public class MessageOutboxMetrics {

    private static final String QUEUE_NAME = "message-outbox";

    private final MessageOutboxTaskMapper messageOutboxTaskMapper;
    private final MessageOutboxProperties properties;
    private final DurableEventQueueMetrics queueMetrics;

    public MessageOutboxMetrics(MessageOutboxTaskMapper messageOutboxTaskMapper,
                                MessageOutboxProperties properties,
                                MeterRegistry meterRegistry) {
        this.messageOutboxTaskMapper = messageOutboxTaskMapper;
        this.properties = properties;
        this.queueMetrics = new DurableEventQueueMetrics(meterRegistry, QUEUE_NAME);
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            long pendingCount = messageOutboxTaskMapper.countByStatus(MessageOutboxTaskStatus.PENDING.name());
            OffsetDateTime oldestCreatedAt = messageOutboxTaskMapper.findOldestPendingCreatedAt();
            long oldestAgeSeconds = oldestCreatedAt != null ? Duration.between(oldestCreatedAt, now).getSeconds() : 0L;
            OffsetDateTime since = now.minusMinutes(1);
            long dispatchedLastMinute = messageOutboxTaskMapper.countSucceededSince(since);
            long failedLastMinute = messageOutboxTaskMapper.countFailedSince(since, properties.getMaxRetry());
            long deadLastMinute = messageOutboxTaskMapper.countDeadSince(since, properties.getMaxRetry());

            queueMetrics.updateSnapshot(
                    pendingCount,
                    oldestAgeSeconds,
                    dispatchedLastMinute,
                    failedLastMinute,
                    deadLastMinute
            );
            log.debug("Message outbox metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min, deadRate={}/min",
                    pendingCount, oldestAgeSeconds, dispatchedLastMinute, failedLastMinute, deadLastMinute);
        } catch (Exception e) {
            log.error("Failed to collect message outbox metrics", e);
        }
    }
}
