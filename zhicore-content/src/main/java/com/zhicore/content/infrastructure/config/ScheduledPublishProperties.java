package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 定时发布配置（R1）
 */
@Data
@Component
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "scheduled.publish")
public class ScheduledPublishProperties {

    /**
     * 扫描批次大小（默认 100）
     */
    @Min(1)
    @Max(1000)
    private int scanBatchSize = 100;

    /**
     * 补偿扫描周期（毫秒，默认 1000）
     */
    @Min(100)
    @Max(60_000)
    private long compensationScanIntervalMs = 1_000;

    /**
     * 补偿扫描初始延迟（毫秒，默认 1000）
     */
    @Min(0)
    @Max(60_000)
    private long compensationInitialDelayMs = 1_000;

    /**
     * 补偿扫描即将到期窗口（秒，默认 60）
     */
    @Min(1)
    @Max(3_600)
    private int upcomingWindowSeconds = 60;

    /**
     * 同一条记录的补偿入队冷却期（秒，默认 15）
     */
    @Min(1)
    @Max(600)
    private int enqueueCooldownSeconds = 15;

    /**
     * claim 超时阈值（秒，默认 120）。
     *
     * <p>超过该阈值仍未释放 claim 的记录，会被补偿扫描视为可回收任务。</p>
     */
    @Min(1)
    @Max(3_600)
    private int claimTimeoutSeconds = 120;

    /**
     * reschedule 最大次数（默认 10）
     */
    @Min(0)
    @Max(100)
    private int maxRescheduleRetries = 10;

    /**
     * publish 最大次数（默认 3）
     */
    @Min(0)
    @Max(20)
    private int maxPublishRetries = 3;

    /**
     * 最大延迟（分钟，默认 30）
     */
    @Min(1)
    @Max(180)
    private int maxDelayMinutes = 30;

    /**
     * 是否优先使用 RocketMQ timer message 作为主触发。
     *
     * <p>开启后，schedule-execute 事件会按 scheduledAt 直接设置 deliverTimeMs；
     * broker 不支持或发送失败时，派发器会回退到基于 scheduledAt 重新计算的 delay level。</p>
     */
    private boolean timerMessageEnabled = true;

    @AssertTrue(message = "scheduled.publish.upcoming-window-seconds 必须大于等于 enqueue-cooldown-seconds")
    public boolean isUpcomingWindowValid() {
        return upcomingWindowSeconds >= enqueueCooldownSeconds;
    }

    @AssertTrue(message = "scheduled.publish.claim-timeout-seconds 必须大于 enqueue-cooldown-seconds")
    public boolean isClaimTimeoutValid() {
        return claimTimeoutSeconds > enqueueCooldownSeconds;
    }

    /**
     * 兼容旧配置：分钟级冷却期会自动转换为秒级配置。
     */
    @Deprecated
    public void setEnqueueCooldownMinutes(Integer enqueueCooldownMinutes) {
        if (enqueueCooldownMinutes != null) {
            this.enqueueCooldownSeconds = Math.toIntExact(Duration.ofMinutes(enqueueCooldownMinutes).getSeconds());
        }
    }

    /**
     * 兼容旧配置：stale-gate-threshold-seconds 会自动映射到 claim-timeout-seconds。
     */
    @Deprecated
    public void setStaleGateThresholdSeconds(Integer staleGateThresholdSeconds) {
        if (staleGateThresholdSeconds != null) {
            this.claimTimeoutSeconds = staleGateThresholdSeconds;
        }
    }
}
