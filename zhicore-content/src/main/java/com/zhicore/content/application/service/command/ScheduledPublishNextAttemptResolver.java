package com.zhicore.content.application.service.command;

import java.time.LocalDateTime;

/**
 * 定时发布补偿任务的 next_attempt_at 计算器。
 *
 * <p>设计约束：
 * 1. 远期任务在进入 upcoming window 前不参与补偿扫描；
 * 2. 窗口内任务允许按冷却期重复补偿，避免单次 delay message 丢失后长期悬空；
 * 3. 已到点任务在补偿重发后也必须进入冷却期，避免扫描线程连续风暴式重投。</p>
 */
public final class ScheduledPublishNextAttemptResolver {

    private ScheduledPublishNextAttemptResolver() {
    }

    public static LocalDateTime resolveCompensationAt(
            LocalDateTime dbNow,
            LocalDateTime scheduledAt,
            int upcomingWindowSeconds,
            int enqueueCooldownSeconds
    ) {
        if (scheduledAt == null) {
            return dbNow;
        }

        if (!scheduledAt.isAfter(dbNow)) {
            return dbNow.plusSeconds(enqueueCooldownSeconds);
        }

        LocalDateTime windowStart = scheduledAt.minusSeconds(upcomingWindowSeconds);
        if (windowStart.isAfter(dbNow)) {
            return windowStart;
        }

        LocalDateTime cooldownAt = dbNow.plusSeconds(enqueueCooldownSeconds);
        return cooldownAt.isAfter(scheduledAt) ? scheduledAt : cooldownAt;
    }
}
