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

    /**
     * 定时发布消费者组
     *
     * 用于定时发布任务的消费者组名称，DLQ topic 会基于此名称生成
     * 默认值：post-schedule-consumer-group
     */
    @NotBlank(message = "定时发布消费者组不能为空")
    private String postScheduleConsumerGroup = "post-schedule-consumer-group";

    /**
     * 定时发布 DLQ Tag
     *
     * 定时发布失败事件的 RocketMQ Tag
     * 默认值：scheduled-publish-dlq
     */
    @NotBlank(message = "定时发布 DLQ Tag 不能为空")
    private String scheduledPublishDlqTag = "scheduled-publish-dlq";
}
