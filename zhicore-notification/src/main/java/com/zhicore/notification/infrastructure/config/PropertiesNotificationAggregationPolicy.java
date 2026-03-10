package com.zhicore.notification.infrastructure.config;

import com.zhicore.notification.application.port.policy.NotificationAggregationPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于配置属性的通知聚合策略。
 */
@Component
public class PropertiesNotificationAggregationPolicy implements NotificationAggregationPolicy {

    private final NotificationAggregationProperties properties;

    public PropertiesNotificationAggregationPolicy(NotificationAggregationProperties properties) {
        this.properties = properties;
    }

    @Override
    public Duration cacheTtl() {
        return Duration.ofSeconds(properties.getCache().getTtl());
    }

    @Override
    public int maxRecentActors() {
        return properties.getDisplay().getMaxRecentActors();
    }
}
