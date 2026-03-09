package com.zhicore.idgenerator.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * ID 生成服务 Sentinel 配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "id-generator.sentinel")
public class IdGeneratorSentinelProperties {

    private boolean enabled = true;

    @Min(1)
    private int snowflakeQps = 500;

    @Min(1)
    private int batchSnowflakeQps = 200;

    @Min(1)
    private int segmentQps = 300;

    @Min(0)
    private int warmUpPeriodSec = 5;
}
