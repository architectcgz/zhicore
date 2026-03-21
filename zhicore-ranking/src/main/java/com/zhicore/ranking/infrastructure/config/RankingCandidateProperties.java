package com.zhicore.ranking.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 热门候选集配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.candidate")
public class RankingCandidateProperties {

    @Valid
    private Posts posts = new Posts();

    @Data
    public static class Posts {

        private boolean enabled = true;

        @Min(1)
        @Max(1000)
        private int size = 200;

        @Min(1000)
        @Max(3_600_000)
        private long refreshInterval = 60_000L;

        @Min(1000)
        @Max(86_400_000)
        private long staleThreshold = 180_000L;
    }
}
