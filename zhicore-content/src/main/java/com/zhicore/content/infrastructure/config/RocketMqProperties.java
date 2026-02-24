package com.zhicore.content.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * RocketMQ 配置属性
 * 
 * 配置 RocketMQ Topic 相关参数，支持 Nacos 动态刷新
 */
@Data
@Component
@Validated
@RefreshScope
@ConfigurationProperties(prefix = "rocketmq.topic")
public class RocketMqProperties {
    
    /**
     * 文章事件 Topic
     * 
     * 用于发布文章相关的集成事件
     * 默认值：ZhiCore-post-events
     */
    @NotBlank(message = "文章事件 Topic 不能为空")
    private String postEvents = "ZhiCore-post-events";
}
