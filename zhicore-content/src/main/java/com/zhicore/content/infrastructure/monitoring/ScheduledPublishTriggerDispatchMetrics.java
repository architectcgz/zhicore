package com.zhicore.content.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 定时发布 trigger 发送路径指标。
 *
 * <p>用于观测 timer message 主路径是否生效，以及回退到 delay level / immediate 的命中率。
 */
@Component
public class ScheduledPublishTriggerDispatchMetrics {

    public static final String QUEUE_NAME = "content-scheduled-publish-trigger";
    public static final String DISPATCH_METRIC_NAME = "domain.event.trigger.dispatch.total";
    public static final String FALLBACK_METRIC_NAME = "domain.event.trigger.dispatch.fallback.total";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> dispatchCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> fallbackCounters = new ConcurrentHashMap<>();

    public ScheduledPublishTriggerDispatchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTimerDispatch() {
        dispatchCounter("timer", "scheduled_at").increment();
    }

    public void recordDelayLevelDispatch(String reason) {
        dispatchCounter("delay_level", reason).increment();
    }

    public void recordImmediateDispatch(String reason) {
        dispatchCounter("immediate", reason).increment();
    }

    public void recordTimerFallback(String reason, String targetPath) {
        fallbackCounter(reason, targetPath).increment();
    }

    private Counter dispatchCounter(String mode, String reason) {
        String key = mode + ":" + reason;
        return dispatchCounters.computeIfAbsent(key, ignored ->
                Counter.builder(DISPATCH_METRIC_NAME)
                        .description("Scheduled publish trigger dispatch count by final delivery path")
                        .tag("queue", QUEUE_NAME)
                        .tag("mode", mode)
                        .tag("reason", reason)
                        .register(meterRegistry)
        );
    }

    private Counter fallbackCounter(String reason, String targetPath) {
        String key = reason + ":" + targetPath;
        return fallbackCounters.computeIfAbsent(key, ignored ->
                Counter.builder(FALLBACK_METRIC_NAME)
                        .description("Scheduled publish trigger fallback count from timer path")
                        .tag("queue", QUEUE_NAME)
                        .tag("source", "timer")
                        .tag("target", targetPath)
                        .tag("reason", reason)
                        .register(meterRegistry)
        );
    }
}
