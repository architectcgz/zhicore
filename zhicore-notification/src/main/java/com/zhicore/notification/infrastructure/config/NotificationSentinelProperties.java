package com.zhicore.notification.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 通知服务 Sentinel 限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "notification.sentinel")
public class NotificationSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int aggregatedQps = 200;

    @Min(1)
    private int unreadCountQps = 400;

    @Min(0)
    private int warmUpPeriodSec = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public int getAggregatedQps() {
        return aggregatedQps;
    }

    public int getUnreadCountQps() {
        return unreadCountQps;
    }

    public int getWarmUpPeriodSec() {
        return warmUpPeriodSec;
    }
}
