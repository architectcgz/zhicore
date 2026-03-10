package com.zhicore.notification.application.port.policy;

import java.time.Duration;

/**
 * 通知聚合配置策略。
 */
public interface NotificationAggregationPolicy {

    Duration cacheTtl();

    int maxRecentActors();
}
