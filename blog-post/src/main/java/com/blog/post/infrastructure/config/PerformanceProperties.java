package com.blog.post.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 性能配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "performance")
public class PerformanceProperties {
    
    /**
     * 慢查询阈值（毫秒）
     */
    @Min(value = 100, message = "慢查询阈值不能少于 100 毫秒")
    private long slowQueryThresholdMs = 1000;
    
    /**
     * 是否启用慢查询日志
     */
    private boolean slowQueryLogEnabled = true;
}
