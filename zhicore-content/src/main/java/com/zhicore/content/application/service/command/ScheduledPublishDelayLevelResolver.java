package com.zhicore.content.application.service.command;

/**
 * RocketMQ 延迟级别换算。
 *
 * <p>返回 0 表示立即发送，不附带 delay level。</p>
 */
public final class ScheduledPublishDelayLevelResolver {

    private ScheduledPublishDelayLevelResolver() {
    }

    public static int resolve(long delaySeconds) {
        if (delaySeconds <= 0) return 0;
        if (delaySeconds <= 1) return 1;
        if (delaySeconds <= 5) return 2;
        if (delaySeconds <= 10) return 3;
        if (delaySeconds <= 30) return 4;
        if (delaySeconds <= 60) return 5;
        if (delaySeconds <= 120) return 6;
        if (delaySeconds <= 180) return 7;
        if (delaySeconds <= 240) return 8;
        if (delaySeconds <= 300) return 9;
        if (delaySeconds <= 360) return 10;
        if (delaySeconds <= 420) return 11;
        if (delaySeconds <= 480) return 12;
        if (delaySeconds <= 540) return 13;
        if (delaySeconds <= 600) return 14;
        if (delaySeconds <= 1200) return 15;
        if (delaySeconds <= 1800) return 16;
        if (delaySeconds <= 3600) return 17;
        return 18;
    }
}
