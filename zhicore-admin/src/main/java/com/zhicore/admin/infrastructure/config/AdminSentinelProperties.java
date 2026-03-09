package com.zhicore.admin.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 管理服务 Sentinel 限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "admin.sentinel")
public class AdminSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int userListQps = 100;

    @Min(1)
    private int postListQps = 100;

    @Min(1)
    private int commentListQps = 100;

    @Min(1)
    private int reportListQps = 80;

    @Min(0)
    private int warmUpPeriodSec = 10;
}
