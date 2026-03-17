package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.common.monitoring.DurableEventQueueMetrics;
import com.zhicore.content.infrastructure.config.OutboxProperties;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbox 堆积监控指标采集器
 * 
 * 定期采集 Outbox 表的关键指标，并通过统一的 durable queue 指标名暴露给 Prometheus。
 * 
 * 监控指标：
 * 1. domain.event.queue.pending.count{queue="content-outbox"} - PENDING 消息数量
 * 2. domain.event.queue.pending.oldest.age.seconds{queue="content-outbox"} - 最老 PENDING 消息年龄（秒）
 * 3. domain.event.queue.dispatch.rate.per.minute{queue="content-outbox"} - 投递速率（条/分钟）
 * 4. domain.event.queue.failure.rate.per.minute{queue="content-outbox"} - 可重试失败速率（条/分钟）
 * 5. domain.event.queue.dead.rate.per.minute{queue="content-outbox"} - 死信速率（条/分钟）
 * 
 * 告警建议：
 * - PENDING 消息数量 > 1000：消息堆积告警
 * - 最老消息年龄 > 600 秒（10分钟）：消息延迟告警
 * - 投递速率 < 10 条/分钟：投递速率过低告警
 * - 失败速率 > 5 条/分钟：失败率过高告警
 * - 死信速率 > 0 条/分钟：死信告警
 * 
 */
@Slf4j
@Component
public class OutboxMetrics {

    private static final String QUEUE_NAME = "content-outbox";

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxProperties outboxProperties;
    private final DurableEventQueueMetrics queueMetrics;

    public OutboxMetrics(OutboxEventMapper outboxEventMapper,
                         OutboxProperties outboxProperties,
                         MeterRegistry meterRegistry) {
        this.outboxEventMapper = outboxEventMapper;
        this.outboxProperties = outboxProperties;
        this.queueMetrics = new DurableEventQueueMetrics(meterRegistry, QUEUE_NAME);
    }
    
    /**
     * 定期采集 Outbox 指标（每分钟）
     * 
     * 采集频率：每 60 秒执行一次
     * 初始延迟：30 秒（避免启动时立即执行）
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            long pendingCount = outboxEventMapper.countByStatus(
                    OutboxEventEntity.OutboxStatus.PENDING.name()
            );

            Instant oldestCreatedAt = outboxEventMapper.findOldestPendingCreatedAt();
            long oldestAgeSeconds = oldestCreatedAt != null
                    ? Duration.between(oldestCreatedAt, Instant.now()).getSeconds()
                    : 0L;

            Instant since = Instant.now().minus(Duration.ofMinutes(1));
            long dispatchedLastMinute = outboxEventMapper.countSucceededSince(since);

            long failedLastMinute = outboxEventMapper.countFailedSince(
                since,
                outboxProperties.getMaxRetry()
            );

            long deadLastMinute = outboxEventMapper.countDeadSince(
                since,
                outboxProperties.getMaxRetry()
            );

            queueMetrics.updateSnapshot(
                    pendingCount,
                    oldestAgeSeconds,
                    dispatchedLastMinute,
                    failedLastMinute,
                    deadLastMinute
            );

            log.debug("Outbox metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min, deadRate={}/min",
                pendingCount, 
                oldestAgeSeconds,
                dispatchedLastMinute,
                failedLastMinute,
                deadLastMinute
            );

        } catch (Exception e) {
            log.error("Failed to collect Outbox metrics", e);
        }
    }
}
