package com.zhicore.search.infrastructure.config;

import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.validation.constraints.Min;

/**
 * 搜索服务 Sentinel 限流配置。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "search.sentinel")
public class SearchSentinelProperties {

    /**
     * 是否启用搜索服务 URL 级 Sentinel 规则。
     */
    private boolean enabled = true;

    @Min(1)
    private int postsQps = 200;

    @Min(1)
    private int suggestQps = 300;

    @Min(1)
    private int hotKeywordsQps = 200;

    @Min(1)
    private int historyQps = 100;

    @Min(0)
    private int warmUpPeriodSec = 10;
}
