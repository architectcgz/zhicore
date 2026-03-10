package com.zhicore.content.application.port.policy;

/**
 * 定时发布策略端口。
 */
public interface ScheduledPublishPolicy {

    int maxRescheduleRetries();

    int maxPublishRetries();

    int maxDelayMinutes();
}
