package com.zhicore.content.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时钟配置
 * <p>
 * 提供系统时钟Bean，用于获取当前时间
 * 使用UTC时区确保时间的一致性
 * 注入Clock接口便于在测试中模拟时间
 */
@Configuration
public class ClockConfig {

    /**
     * 提供系统UTC时钟
     *
     * @return UTC时区的系统时钟
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
