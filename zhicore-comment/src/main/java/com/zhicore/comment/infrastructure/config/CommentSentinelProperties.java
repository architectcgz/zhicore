package com.zhicore.comment.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 评论服务 Sentinel 读接口限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "comment.sentinel")
public class CommentSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int commentDetailQps = 200;

    @Min(1)
    private int topLevelPageQps = 150;

    @Min(1)
    private int topLevelCursorQps = 250;

    @Min(1)
    private int repliesPageQps = 150;

    @Min(1)
    private int repliesCursorQps = 250;

    @Min(1)
    private int commentLikedQps = 300;

    @Min(1)
    private int batchCommentLikedQps = 200;

    @Min(1)
    private int commentLikeCountQps = 300;

    @Min(1)
    private int adminQueryCommentsQps = 120;

    @Min(0)
    private int warmUpPeriodSec = 10;
}
