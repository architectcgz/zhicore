package com.zhicore.migration.service.gray;

import java.util.Set;

/**
 * 灰度服务运行所需的只读配置。
 */
public record GrayReleaseSettings(
        boolean enabled,
        int trafficRatio,
        Set<String> whitelistUsers,
        Set<String> blacklistUsers,
        AlertSettings alert
) {

    public record AlertSettings(
            double errorRateThreshold,
            long latencyThresholdMs
    ) {
    }
}
