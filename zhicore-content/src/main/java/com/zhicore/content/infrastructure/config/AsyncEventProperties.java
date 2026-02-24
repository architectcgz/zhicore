package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 异步事件线程池配置（R15）
 *
 * 默认值：
 * - core/max/queue=8/16/1000
 * - threadPrefix=async-event-
 */
@Data
@Component
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "async.event")
public class AsyncEventProperties {

    @Min(1)
    @Max(128)
    private int corePoolSize = 8;

    @Min(1)
    @Max(256)
    private int maxPoolSize = 16;

    @Min(0)
    @Max(100_000)
    private int queueCapacity = 1000;

    private String threadNamePrefix = "async-event-";
}

