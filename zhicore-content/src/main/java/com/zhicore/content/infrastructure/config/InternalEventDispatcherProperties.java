package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 内部事件派发调度配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "internal-event.dispatcher")
public class InternalEventDispatcherProperties {

    @Min(1)
    @Max(1000)
    private int batchSize = 100;

    @Min(1000)
    @Max(60000)
    private long scanInterval = 5000;

    @Min(1)
    @Max(20)
    private int maxRetry = 10;

    @Min(1)
    @Max(3600)
    private long maxBackoffSeconds = 300;
}
