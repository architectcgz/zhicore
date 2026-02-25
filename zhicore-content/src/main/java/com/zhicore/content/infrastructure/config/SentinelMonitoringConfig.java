package com.zhicore.content.infrastructure.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 监控配置
 * 配置 Sentinel 资源保护和降级策略
 */
@Configuration
public class SentinelMonitoringConfig {

    /**
     * 启用 @SentinelResource 注解支持
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
