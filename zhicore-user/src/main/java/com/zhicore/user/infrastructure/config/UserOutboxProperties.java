package com.zhicore.user.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 用户服务 outbox 派发配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "user.outbox")
public class UserOutboxProperties {

    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @Min(1)
    @Max(32)
    private int workerCount = 4;

    @Min(1000)
    @Max(60000)
    private long scanInterval = 5000;

    @Min(5)
    @Max(3600)
    private long claimTimeoutSeconds = 60;

    @Min(1)
    @Max(20)
    private int maxRetry = 10;

    @Min(1)
    @Max(3600)
    private long maxBackoffSeconds = 300;
}
