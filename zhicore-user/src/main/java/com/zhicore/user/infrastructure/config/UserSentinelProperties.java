package com.zhicore.user.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 用户服务 Sentinel 读接口限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "user.sentinel")
public class UserSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int userDetailQps = 250;

    @Min(1)
    private int userSimpleQps = 400;

    @Min(1)
    private int batchUserSimpleQps = 200;

    @Min(1)
    private int strangerMessageSettingQps = 300;

    @Min(1)
    private int followersQps = 150;

    @Min(1)
    private int followingsQps = 150;

    @Min(1)
    private int followStatsQps = 250;

    @Min(1)
    private int isFollowingQps = 400;

    @Min(1)
    private int checkInStatsQps = 200;

    @Min(1)
    private int monthlyCheckInBitmapQps = 150;

    @Min(1)
    private int blockedUsersQps = 150;

    @Min(1)
    private int isBlockedQps = 300;

    @Min(1)
    private int queryUsersQps = 100;

    @Min(0)
    private int warmUpPeriodSec = 10;
}
