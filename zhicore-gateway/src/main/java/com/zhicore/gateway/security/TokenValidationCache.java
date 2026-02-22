package com.zhicore.gateway.security;

import com.zhicore.gateway.config.JwtProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Token 验证结果缓存
 * 
 * 优化点：
 * - 使用 Caffeine 本地缓存提升性能
 * - 使用 ThreadLocal MessageDigest 避免 synchronized 瓶颈
 * - Token Hash 作为 Key，避免内存中存储完整 Token
 * - 支持配置化开关，便于故障排查
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class TokenValidationCache {
    
    private final Cache<String, ValidationResult> cache;
    private final boolean cacheEnabled;
    
    // Use ThreadLocal to avoid synchronized bottleneck
    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    });
    
    public TokenValidationCache(JwtProperties jwtProperties) {
        // Check if cache is enabled
        this.cacheEnabled = jwtProperties.getCache() != null && 
                           jwtProperties.getCache().isEnabled();
        
        if (!cacheEnabled) {
            this.cache = null;
            log.warn("TokenValidationCache is DISABLED - all tokens will be validated without caching");
            return;
        }
        
        // Initialize cache with Caffeine
        int maxSize = jwtProperties.getCache().getMaxSize();
        int ttlMinutes = jwtProperties.getCache().getTtlMinutes();
        
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
        
        log.info("TokenValidationCache initialized - maxSize: {}, ttl: {} minutes", 
                 maxSize, ttlMinutes);
    }
    
    /**
     * 获取缓存的验证结果
     */
    public Optional<ValidationResult> get(String token) {
        if (!cacheEnabled || cache == null) {
            return Optional.empty();
        }
        String key = hashToken(token);
        ValidationResult result = cache.getIfPresent(key);
        return Optional.ofNullable(result);
    }
    
    /**
     * 缓存验证结果
     */
    public void put(String token, ValidationResult result) {
        if (!cacheEnabled || cache == null) {
            return;
        }
        String key = hashToken(token);
        cache.put(key, result);
    }
    
    /**
     * 使缓存的 Token 失效
     */
    public void invalidate(String token) {
        if (!cacheEnabled || cache == null) {
            return;
        }
        String key = hashToken(token);
        cache.invalidate(key);
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        if (!cacheEnabled || cache == null) {
            return CacheStats.builder()
                    .hitCount(0)
                    .missCount(0)
                    .hitRate(0.0)
                    .evictionCount(0)
                    .size(0)
                    .build();
        }
        var stats = cache.stats();
        return CacheStats.builder()
                .hitCount(stats.hitCount())
                .missCount(stats.missCount())
                .hitRate(stats.hitRate())
                .evictionCount(stats.evictionCount())
                .size(cache.estimatedSize())
                .build();
    }
    
    /**
     * 使用 SHA-256 哈希 Token 作为缓存 Key
     * 
     * 使用 ThreadLocal 避免 synchronized 瓶颈
     * 防止内存转储时暴露完整 Token
     */
    private String hashToken(String token) {
        MessageDigest digest = SHA256_DIGEST.get();
        digest.reset();  // Important: reset before use
        byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
