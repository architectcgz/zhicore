package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbox 堆积监控指标采集器
 * 
 * 定期采集 Outbox 表的关键指标，用于监控消息堆积情况
 * 
 * 监控指标：
 * 1. outbox.pending.count - PENDING 消息数量
 * 2. outbox.pending.oldest.age.seconds - 最老 PENDING 消息年龄（秒）
 * 3. outbox.dispatch.rate.per.minute - 投递速率（条/分钟）
 * 4. outbox.failure.rate.per.minute - 失败速率（条/分钟）
 * 
 * 告警建议：
 * - PENDING 消息数量 > 1000：消息堆积告警
 * - 最老消息年龄 > 600 秒（10分钟）：消息延迟告警
 * - 投递速率 < 10 条/分钟：投递速率过低告警
 * - 失败速率 > 5 条/分钟：失败率过高告警
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMetrics {
    
    private final OutboxEventMapper outboxEventMapper;
    private final MeterRegistry meterRegistry;
    
    /**
     * 定期采集 Outbox 指标（每分钟）
     * 
     * 采集频率：每 60 秒执行一次
     * 初始延迟：30 秒（避免启动时立即执行）
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void collectMetrics() {
        try {
            // 1. PENDING 消息数量
            long pendingCount = outboxEventMapper.countByStatus(
                OutboxEventEntity.OutboxStatus.PENDING.name()
            );
            meterRegistry.gauge("outbox.pending.count", pendingCount);
            
            // 2. 最老的 PENDING 消息年龄（秒）
            Instant oldestCreatedAt = outboxEventMapper.findOldestPendingCreatedAt();
            if (oldestCreatedAt != null) {
                long ageSeconds = Duration.between(oldestCreatedAt, Instant.now()).getSeconds();
                meterRegistry.gauge("outbox.pending.oldest.age.seconds", ageSeconds);
            } else {
                // 没有 PENDING 消息，设置为 0
                meterRegistry.gauge("outbox.pending.oldest.age.seconds", 0);
            }
            
            // 3. 投递速率（过去1分钟）
            long dispatchedLastMinute = outboxEventMapper.countDispatchedSince(
                Instant.now().minus(Duration.ofMinutes(1))
            );
            meterRegistry.gauge("outbox.dispatch.rate.per.minute", dispatchedLastMinute);
            
            // 4. 失败率（过去1分钟）
            long failedLastMinute = outboxEventMapper.countFailedSince(
                Instant.now().minus(Duration.ofMinutes(1))
            );
            meterRegistry.gauge("outbox.failure.rate.per.minute", failedLastMinute);
            
            // 记录调试日志
            log.debug("Outbox metrics collected: pending={}, oldestAge={}s, dispatchRate={}/min, failureRate={}/min",
                pendingCount, 
                oldestCreatedAt != null ? Duration.between(oldestCreatedAt, Instant.now()).getSeconds() : 0,
                dispatchedLastMinute,
                failedLastMinute
            );
            
        } catch (Exception e) {
            log.error("Failed to collect Outbox metrics", e);
        }
    }
}
