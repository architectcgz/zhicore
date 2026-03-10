package com.zhicore.content.infrastructure.config;

import com.zhicore.content.application.port.policy.ScheduledPublishPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于配置属性的定时发布策略实现。
 */
@Component
@RequiredArgsConstructor
public class PropertiesScheduledPublishPolicy implements ScheduledPublishPolicy {

    private final ScheduledPublishProperties properties;

    @Override
    public int maxRescheduleRetries() {
        return properties.getMaxRescheduleRetries();
    }

    @Override
    public int maxPublishRetries() {
        return properties.getMaxPublishRetries();
    }

    @Override
    public int maxDelayMinutes() {
        return properties.getMaxDelayMinutes();
    }
}
