package com.zhicore.message.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 消息 Outbox 调度配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "message.outbox")
public class MessageOutboxProperties {

    /**
     * 每批扫描的最大任务数。
     */
    @Min(1)
    @Max(500)
    private int batchSize = 100;

    /**
     * 并发 worker 数。
     */
    @Min(1)
    @Max(32)
    private int workerCount = 4;

    /**
     * 调度器扫描间隔（毫秒）。
     */
    @Min(1000)
    @Max(60000)
    private long scanInterval = 5000;

    /**
     * PROCESSING 任务 claim 超时秒数。
     */
    @Min(5)
    @Max(3600)
    private long claimTimeoutSeconds = 60;

    /**
     * 最大重试次数，超过后标记为 DEAD。
     */
    @Min(1)
    @Max(20)
    private int maxRetry = 10;

    /**
     * 最大回退秒数。
     */
    @Min(1)
    @Max(3600)
    private long maxBackoffSeconds = 300;
}
