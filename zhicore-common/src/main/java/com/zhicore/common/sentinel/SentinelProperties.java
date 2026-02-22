package com.zhicore.common.sentinel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * Sentinel 配置属性
 * 支持 Nacos 配置热更新
 *
 * @author ZhiCore Team
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "sentinel")
public class SentinelProperties {

    /**
     * 错误率阈值（0-1），超过此比例触发熔断
     */
    private double errorRatioThreshold = 0.5;

    /**
     * 慢调用比例阈值（0-1），超过此比例触发熔断
     */
    private double slowRatioThreshold = 0.5;

    /**
     * 慢调用阈值（毫秒），超过此时间视为慢调用
     */
    private int slowRequestMs = 1000;

    /**
     * 熔断恢复时间（秒）
     */
    private int recoveryTimeoutSeconds = 30;

    /**
     * 最小请求数，低于此数量不触发熔断
     */
    private int minRequestAmount = 10;

    /**
     * 统计时间窗口（毫秒）
     */
    private int statIntervalMs = 10000;

    /**
     * 默认 QPS 限制
     */
    private int defaultQpsLimit = 1000;

    /**
     * Feign 超时时间（毫秒）
     */
    private int feignConnectTimeout = 5000;

    /**
     * Feign 读取超时时间（毫秒）
     */
    private int feignReadTimeout = 10000;

    /**
     * 重试次数
     */
    private int retryMaxAttempts = 3;

    /**
     * 重试间隔（毫秒）
     */
    private int retryIntervalMs = 1000;
}
