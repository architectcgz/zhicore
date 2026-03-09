package com.zhicore.common.sentinel;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 注解切面公共配置。
 */
@Configuration
public class SentinelAspectConfig {

    @Bean
    @ConditionalOnMissingBean(SentinelResourceAspect.class)
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
