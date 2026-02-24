package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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
     * 入队冷却期（分钟，默认 2）
     */
    @Min(0)
    @Max(60)
    private int enqueueCooldownMinutes = 2;

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
}

