package com.zhicore.ranking.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 排行榜缓冲配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.buffer")
public class RankingBufferProperties {
    
    /**
     * 刷写间隔（毫秒）
     * 默认 5000ms（5秒）
     */
    @Min(value = 1000, message = "刷写间隔不能少于 1000 毫秒")
    private long flushInterval = 5000L;
    
    /**
     * 批量刷写大小
     * 默认 1000
     */
    @Min(value = 100, message = "批量刷写大小不能少于 100")
    private int batchSize = 1000;
}
