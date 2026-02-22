package com.zhicore.content.infrastructure.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置类
 * 配置 Micrometer 和 Prometheus 监控
 */
@Configuration
public class MonitoringConfig {

    /**
     * 启用 @Timed 注解支持
     * 可以在方法上使用 @Timed 注解来自动记录方法执行时间
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
