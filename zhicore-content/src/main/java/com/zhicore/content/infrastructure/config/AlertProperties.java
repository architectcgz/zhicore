package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 告警配置属性
 * 
 * 配置告警相关参数，包括去重窗口等
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "zhicore.post.alert")
public class AlertProperties {
    
    /**
     * 告警去重窗口时间（分钟）
     * 
     * 在此时间窗口内，相同类型的告警只发送一次
     * 默认值：5 分钟
     * 取值范围：1-60 分钟
     */
    @Min(value = 1, message = "告警去重窗口必须至少为 1 分钟")
    @Max(value = 60, message = "告警去重窗口不能超过 60 分钟")
    private int dedupWindowMinutes = 5;
}
