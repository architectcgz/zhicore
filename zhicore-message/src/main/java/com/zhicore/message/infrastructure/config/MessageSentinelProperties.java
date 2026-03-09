package com.zhicore.message.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 消息服务 Sentinel 限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "message.sentinel")
public class MessageSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int conversationListQps = 200;

    @Min(1)
    private int conversationDetailQps = 300;

    @Min(1)
    private int messageHistoryQps = 250;

    @Min(1)
    private int unreadCountQps = 400;

    @Min(0)
    private int warmUpPeriodSec = 10;
}
