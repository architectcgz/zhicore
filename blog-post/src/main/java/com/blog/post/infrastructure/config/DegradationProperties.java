package com.blog.post.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 降级策略配置属性
 * 从 application.yml 读取降级相关配置
 * 
 * Requirements: 12.6
 */
@Data
@Component
@ConfigurationProperties(prefix = "post.degradation")
public class DegradationProperties {

    /**
     * 是否启用降级功能
     * 默认: true
     */
    private boolean enabled = true;

    /**
     * MongoDB 连接失败降级
     * 是否在 MongoDB 不可用时降级为仅使用 PostgreSQL
     * 默认: true
     */
    private boolean fallbackOnMongoFailure = true;

    /**
     * 降级触发条件 - 错误率阈值
     * MongoDB 操作错误率超过此值时触发降级
     * 默认: 0.5 (50%)
     */
    private double errorRateThreshold = 0.5;

    /**
     * 降级触发条件 - 慢调用比例阈值
     * MongoDB 操作慢调用比例超过此值时触发降级
     * 默认: 0.8 (80%)
     */
    private double slowCallRateThreshold = 0.8;

    /**
     * 慢调用阈值（毫秒）
     * 超过此时间的调用被认为是慢调用
     * 默认: 1000 毫秒
     */
    private int slowCallThresholdMs = 1000;

    /**
     * 降级时间窗口（秒）
     * 统计错误率和慢调用率的时间窗口
     * 默认: 60 秒
     */
    private int timeWindowSeconds = 60;

    /**
     * 最小请求数
     * 时间窗口内请求数少于此值时不触发降级
     * 默认: 10
     */
    private int minRequestAmount = 10;

    /**
     * 降级持续时间（秒）
     * 触发降级后，保持降级状态的时间
     * 默认: 60 秒
     */
    private int degradationDurationSeconds = 60;

    /**
     * 降级恢复策略
     * - auto: 自动恢复（降级时间到期后）
     * - manual: 手动恢复
     * 默认: auto
     */
    private String recoveryStrategy = "auto";

    /**
     * 是否在降级时发送告警
     * 默认: true
     */
    private boolean alertOnDegradation = true;

    /**
     * 降级时的默认行为
     * - return-metadata-only: 仅返回元数据
     * - return-cached: 返回缓存数据
     * - throw-exception: 抛出异常
     * 默认: return-metadata-only
     */
    private String fallbackBehavior = "return-metadata-only";

    /**
     * 是否记录降级日志
     * 默认: true
     */
    private boolean logEnabled = true;

    /**
     * 降级指标收集
     * 是否收集降级相关的监控指标
     * 默认: true
     */
    private boolean metricsEnabled = true;
}
