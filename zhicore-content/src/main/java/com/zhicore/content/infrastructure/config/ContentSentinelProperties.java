package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 文章服务 Sentinel 读接口限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "content.sentinel")
public class ContentSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int postDetailQps = 300;

    @Min(1)
    private int postListQps = 200;

    @Min(1)
    private int postContentQps = 150;

    @Min(1)
    private int tagDetailQps = 200;

    @Min(1)
    private int tagListQps = 150;

    @Min(1)
    private int tagSearchQps = 150;

    @Min(1)
    private int tagPostsQps = 120;

    @Min(1)
    private int hotTagsQps = 120;

    @Min(1)
    private int postLikedQps = 250;

    @Min(1)
    private int batchPostLikedQps = 150;

    @Min(1)
    private int postLikeCountQps = 250;

    @Min(1)
    private int postFavoritedQps = 250;

    @Min(1)
    private int batchPostFavoritedQps = 150;

    @Min(1)
    private int postFavoriteCountQps = 250;

    @Min(1)
    private int adminQueryPostsQps = 100;

    @Min(1)
    private int outboxFailedQps = 80;

    @Min(0)
    private int warmUpPeriodSec = 10;

    @Min(1000)
    private long reconcileIntervalMs = 30000L;
}
