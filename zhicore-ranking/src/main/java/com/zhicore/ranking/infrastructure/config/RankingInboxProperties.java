package com.zhicore.ranking.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Ranking inbox 聚合配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.inbox")
public class RankingInboxProperties {

    @Min(1000)
    private long scanInterval = 5000L;

    @Min(1)
    private int batchSize = 500;

    @Min(5)
    private long leaseSeconds = 60L;

    @Min(1)
    private int maxRetry = 10;

    @Min(16)
    private int appliedEventWindowSize = 2048;

    @Min(1)
    private int doneRetentionDays = 400;
}
