package com.zhicore.ranking.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Ranking ledger/bucket pipeline 配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.pipeline")
public class RankingPipelineProperties {

    @Min(1)
    @Max(300)
    private int bucketWindowSeconds = 10;

    @Min(0)
    @Max(3600)
    private int flushDelaySeconds = 0;

    @Min(1000)
    @Max(60000)
    private long flushInterval = 5000L;

    @Min(1)
    @Max(2000)
    private int flushBatchSize = 200;

    public int getEffectiveFlushDelaySeconds() {
        return flushDelaySeconds > 0 ? flushDelaySeconds : bucketWindowSeconds;
    }
}
