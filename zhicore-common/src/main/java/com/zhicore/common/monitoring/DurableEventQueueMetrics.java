package com.zhicore.common.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * durable event queue 的统一指标封装。
 *
 * <p>统一暴露以下 Prometheus 指标：
 * - domain.event.queue.pending.count
 * - domain.event.queue.pending.oldest.age.seconds
 * - domain.event.queue.dispatch.rate.per.minute
 * - domain.event.queue.failure.rate.per.minute
 * - domain.event.queue.dead.rate.per.minute
 *
 * <p>通过 queue tag 区分不同服务/队列，避免每个模块重复定义 gauge。
 */
public final class DurableEventQueueMetrics {

    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();
    private final AtomicLong dispatchRatePerMinute = new AtomicLong();
    private final AtomicLong failureRatePerMinute = new AtomicLong();
    private final AtomicLong deadRatePerMinute = new AtomicLong();

    public DurableEventQueueMetrics(MeterRegistry meterRegistry, String queueName) {
        Gauge.builder("domain.event.queue.pending.count", pendingCount, AtomicLong::get)
                .description("Current pending event count in durable queue")
                .tag("queue", queueName)
                .register(meterRegistry);
        Gauge.builder("domain.event.queue.pending.oldest.age.seconds", oldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest pending event in seconds")
                .tag("queue", queueName)
                .register(meterRegistry);
        Gauge.builder("domain.event.queue.dispatch.rate.per.minute", dispatchRatePerMinute, AtomicLong::get)
                .description("Succeeded dispatch count in the last minute")
                .tag("queue", queueName)
                .register(meterRegistry);
        Gauge.builder("domain.event.queue.failure.rate.per.minute", failureRatePerMinute, AtomicLong::get)
                .description("Retryable failure count in the last minute")
                .tag("queue", queueName)
                .register(meterRegistry);
        Gauge.builder("domain.event.queue.dead.rate.per.minute", deadRatePerMinute, AtomicLong::get)
                .description("Dead-letter count in the last minute")
                .tag("queue", queueName)
                .register(meterRegistry);
    }

    public void updateSnapshot(long pendingCountValue,
                               long oldestPendingAgeSecondsValue,
                               long dispatchRatePerMinuteValue,
                               long failureRatePerMinuteValue,
                               long deadRatePerMinuteValue) {
        pendingCount.set(pendingCountValue);
        oldestPendingAgeSeconds.set(Math.max(oldestPendingAgeSecondsValue, 0L));
        dispatchRatePerMinute.set(Math.max(dispatchRatePerMinuteValue, 0L));
        failureRatePerMinute.set(Math.max(failureRatePerMinuteValue, 0L));
        deadRatePerMinute.set(Math.max(deadRatePerMinuteValue, 0L));
    }
}
