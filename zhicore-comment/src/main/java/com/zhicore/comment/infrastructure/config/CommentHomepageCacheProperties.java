package com.zhicore.comment.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 首页评论缓存配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "comment.homepage-cache")
public class CommentHomepageCacheProperties {

    @Min(1)
    private long ttlSeconds = 30;

    @Min(0)
    private int ttlJitterSeconds = 3;

    @Min(0)
    private long rankingSyncInitialDelayMs = 5000;

    @Min(1000)
    private long rankingSyncIntervalMs = 30000;

    @Min(0)
    private long accessRecordIntervalMs = 1000;

    @Min(0)
    private long peerBackfillMaxWaitMs = 200;

    @Min(1)
    private long peerBackfillPollIntervalMs = 20;
}
