package com.zhicore.comment.infrastructure.monitoring;

import com.zhicore.comment.domain.model.OutboxEventStatus;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import com.zhicore.comment.infrastructure.config.CommentOutboxProperties;
import com.zhicore.common.monitoring.DurableEventQueueMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 评论服务 outbox 观测指标。
 */
@Slf4j
@Component
public class CommentOutboxMetrics {

    private static final String QUEUE_NAME = "comment-outbox";

    private final OutboxEventRepository outboxEventRepository;
    private final CommentOutboxProperties properties;
    private final DurableEventQueueMetrics queueMetrics;

    public CommentOutboxMetrics(OutboxEventRepository outboxEventRepository,
                                CommentOutboxProperties properties,
                                MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.queueMetrics = new DurableEventQueueMetrics(meterRegistry, QUEUE_NAME);
    }

    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            LocalDateTime now = LocalDateTime.now();
            long pendingCount = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
            LocalDateTime oldestCreatedAt = outboxEventRepository.findOldestPendingCreatedAt();
            long oldestAgeSeconds = oldestCreatedAt != null ? Duration.between(oldestCreatedAt, now).getSeconds() : 0L;
            LocalDateTime since = now.minusMinutes(1);
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
            log.debug("Comment outbox metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min, deadRate={}/min",
                    pendingCount, oldestAgeSeconds, dispatchedLastMinute, failedLastMinute, deadLastMinute);
        } catch (Exception e) {
            log.error("Failed to collect comment outbox metrics", e);
        }
    }
}
