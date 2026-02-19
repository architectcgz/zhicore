package com.blog.post.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置
 * 
 * 配置 MyBatis 相关的拦截器和插件
 *
 * @author Blog Team
 */
@Configuration
public class MyBatisConfig {

    /**
     * 注册慢查询拦截器
     */
    @Bean
    public SlowQueryInterceptor slowQueryInterceptor(PerformanceProperties performanceProperties) {
        return new SlowQueryInterceptor(performanceProperties);
    }
}
