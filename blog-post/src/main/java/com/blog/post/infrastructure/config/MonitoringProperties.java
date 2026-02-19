package com.blog.post.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 监控配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {
    
    /**
     * 存储空间告警阈值（百分比，0-100）
     */
    @DecimalMin(value = "0.0", message = "存储阈值不能低于 0")
    @DecimalMax(value = "100.0", message = "存储阈值不能超过 100")
    private double storageThreshold = 80.0;
    
    /**
     * 查询性能阈值（毫秒）
     */
    @Min(value = 100, message = "查询性能阈值不能少于 100 毫秒")
    private double queryPerformanceThreshold = 500.0;
    
    /**
     * 双写失败率阈值（0-1之间）
     */
    @DecimalMin(value = "0.0", message = "双写失败率阈值不能低于 0")
    @DecimalMax(value = "1.0", message = "双写失败率阈值不能超过 1")
    private double dualWriteFailureRateThreshold = 0.05;
    
    /**
     * 慢查询阈值（毫秒）
     */
    @Min(value = 100, message = "慢查询阈值不能少于 100 毫秒")
    private long slowQueryThresholdMs = 1000;
}
