package com.zhicore.migration.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 灰度发布配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "gray")
public class GrayReleaseProperties {

    /**
     * 是否启用灰度发布
     */
    private boolean enabled = false;

    /**
     * 灰度流量比例 (0-100)
     */
    private int trafficRatio = 5;

    /**
     * 灰度用户白名单（优先进入灰度）
     */
    private Set<String> whitelistUsers = new HashSet<>();

    /**
     * 灰度用户黑名单（永不进入灰度）
     */
    private Set<String> blacklistUsers = new HashSet<>();

    /**
     * 数据对账间隔（秒）
     */
    private int reconciliationInterval = 300;

    /**
     * 监控告警配置
     */
    private Alert alert = new Alert();

    @Data
    public static class Alert {
        /**
         * 错误率阈值
         */
        private double errorRateThreshold = 0.01;

        /**
         * 延迟阈值（毫秒）
         */
        private long latencyThresholdMs = 500;
    }
}
