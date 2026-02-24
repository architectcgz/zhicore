package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox 配置属性
 * 
 * 配置 Outbox 投递器的相关参数，支持 Nacos 动态刷新
 */
@Data
@Component
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "outbox.dispatcher")
public class OutboxProperties {
    
    /**
     * 每次扫描的批量大小
     * 
     * 投递器每次从 Outbox 表中读取的最大事件数量
     * 默认值：100
     * 取值范围：1-1000
     */
    @Min(value = 1, message = "批量大小必须至少为 1")
    @Max(value = 1000, message = "批量大小不能超过 1000")
    private int batchSize = 100;
    
    /**
     * 最大重试次数
     * 
     * 投递失败后的最大重试次数，超过后标记为 FAILED
     * 默认值：3
     * 取值范围：1-10
     */
    @Min(value = 1, message = "最大重试次数必须至少为 1")
    @Max(value = 10, message = "最大重试次数不能超过 10")
    private int maxRetry = 3;
    
    /**
     * 扫描间隔（毫秒）
     * 
     * 投递器扫描 Outbox 表的时间间隔
     * 默认值：5000 毫秒（5秒）
     * 取值范围：1000-60000 毫秒（1秒-60秒）
     */
    @Min(value = 1000, message = "扫描间隔必须至少为 1000 毫秒")
    @Max(value = 60000, message = "扫描间隔不能超过 60000 毫秒")
    private long scanInterval = 5000;
}
