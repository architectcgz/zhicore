package com.zhicore.gateway.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT 配置属性
 *
 * @author ZhiCore Team
 */
@Slf4j
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥
     */
    private String secret;

    /**
     * Access Token 过期时间（秒）
     */
    private long accessTokenExpiration = 7200;

    /**
     * Refresh Token 过期时间（秒）
     */
    private long refreshTokenExpiration = 604800;

    /**
     * 白名单路径（不需要认证）
     */
    private List<String> whitelist = new ArrayList<>(List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/posts",
            "/api/v1/posts/*",
            "/api/v1/users/*/profile",
            "GET:/api/v1/users/*",
            "GET:/api/v1/users/*/posts",
            "GET:/api/v1/comments/*",
            "GET:/api/v1/comments/post/*/page",
            "GET:/api/v1/comments/*/like-count",
            "GET:/api/v1/comments/*/replies/page",
            "GET:/api/v1/files/*",
            "/ws/message/**",
            "/ws/notification/**",
            "/api/v1/search/**",
            "/api/v1/ranking/**",
            "/actuator/**",
            "/api/v1/id/**"
    ));

    /**
     * 缓存配置
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * JWT Token 验证缓存配置
     */
    @Data
    public static class CacheConfig {
        /**
         * 是否启用缓存（默认启用）
         */
        private boolean enabled = true;

        /**
         * 缓存最大数量（默认 10,000 个 Token）
         */
        private int maxSize = 10000;

        /**
         * 缓存过期时间（分钟，默认 5 分钟）
         */
        private int ttlMinutes = 5;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConfig() {
        log.info("JWT Properties loaded:");
        log.info("  secret: {}", secret != null ? "***configured***" : "NULL");
        log.info("  accessTokenExpiration: {} seconds", accessTokenExpiration);
        log.info("  refreshTokenExpiration: {} seconds", refreshTokenExpiration);
        log.info("  whitelist size: {}", whitelist.size());
        log.info("  cache.enabled: {}", cache.enabled);
        log.info("  cache.maxSize: {}", cache.maxSize);
        log.info("  cache.ttlMinutes: {}", cache.ttlMinutes);
    }
}
