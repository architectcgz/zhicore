package com.zhicore.ranking.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Ranking 快照刷新配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.snapshot")
public class RankingSnapshotProperties {

    @Min(5000)
    private long refreshInterval = 60000L;

    @Min(10)
    private int topSize = 10000;
}
