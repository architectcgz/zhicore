package com.zhicore.gateway.config;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
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

    private static final List<String> DEFAULT_DEV_ORIGIN_PATTERNS = List.of(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]"
    );

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = bindList("cors.allowed-origins");
        List<String> allowedOriginPatterns = bindList("cors.allowed-origin-patterns");
        List<String> devOriginPatterns = bindList("cors.dev-origin-patterns");
        long maxAge = environment.getProperty("cors.max-age", Long.class, 3600L);
        
        // 环境检测：判断是否为开发环境
        boolean isDevelopment = isDevelopmentEnvironment();
        
        if (isDevelopment) {
            // 开发环境：使用配置的 dev origin patterns
            (devOriginPatterns.isEmpty() ? DEFAULT_DEV_ORIGIN_PATTERNS : devOriginPatterns)
                    .forEach(config::addAllowedOriginPattern);
        } else {
            // 生产环境：使用配置的具体 origins
            if (!allowedOrigins.isEmpty()) {
                allowedOrigins.forEach(config::addAllowedOrigin);
            }
        }
        
        // 如果配置了额外的 origin patterns，也添加进去
        if (!allowedOriginPatterns.isEmpty()) {
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

    private List<String> bindList(String propertyName) {
        // YAML 数组会以 indexed properties 形式进入 Environment，使用 Binder 才能稳定读取成 List。
        return Binder.get(environment)
                .bind(propertyName, Bindable.listOf(String.class))
                .orElse(List.of());
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
