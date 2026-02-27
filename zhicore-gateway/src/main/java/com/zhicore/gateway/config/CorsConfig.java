package com.zhicore.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置
 * 
 * @author ZhiCore Team
 * @since 2026-02-08
 */
@Configuration
public class CorsConfig {

    private final Environment environment;
    
    @Value("${cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Value("${cors.allowed-origin-patterns:}")
    private List<String> allowedOriginPatterns;

    @Value("${cors.dev-origin-patterns:http://localhost:[*],http://127.0.0.1:[*]}")
    private List<String> devOriginPatterns;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 环境检测：判断是否为开发环境
        boolean isDevelopment = isDevelopmentEnvironment();
        
        if (isDevelopment) {
            // 开发环境：使用配置的 dev origin patterns
            devOriginPatterns.forEach(config::addAllowedOriginPattern);
        } else {
            // 生产环境：使用配置的具体 origins
            if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
                allowedOrigins.forEach(config::addAllowedOrigin);
            }
        }
        
        // 如果配置了额外的 origin patterns，也添加进去
        if (allowedOriginPatterns != null && !allowedOriginPatterns.isEmpty()) {
            allowedOriginPatterns.forEach(config::addAllowedOriginPattern);
        }
        
        // 允许的 HTTP 方法
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedMethod("PATCH");
        
        // 允许的请求头
        config.addAllowedHeader("*");
        
        // 允许携带凭证（cookies）
        config.setAllowCredentials(true);
        
        // 预检请求的有效期（秒）
        config.setMaxAge(maxAge);
        
        // 暴露的响应头
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Type");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }
    
    /**
     * 判断是否为开发环境
     * 
     * @return true 如果是开发环境
     */
    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 || 
               Arrays.asList(activeProfiles).contains("dev") ||
               Arrays.asList(activeProfiles).contains("local");
    }
}
