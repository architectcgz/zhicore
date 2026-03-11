package com.zhicore.gateway.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhicore.gateway.application.model.ValidationResult;
import com.zhicore.gateway.application.port.store.CacheStats;
import com.zhicore.gateway.application.port.store.TokenValidationStore;
import com.zhicore.gateway.config.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 基于 Caffeine 的 token 验证结果缓存实现。
 */
@Slf4j
@Component
public class CaffeineTokenValidationStore implements TokenValidationStore {

    private static final ThreadLocal<MessageDigest> SHA256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    });

    private final Cache<String, ValidationResult> cache;
    private final boolean cacheEnabled;

    public CaffeineTokenValidationStore(JwtProperties jwtProperties) {
        this.cacheEnabled = jwtProperties.getCache() != null
                && jwtProperties.getCache().isEnabled();

        if (!cacheEnabled) {
            this.cache = null;
            log.warn("TokenValidationStore is DISABLED - all tokens will be validated without caching");
            return;
        }

        int maxSize = jwtProperties.getCache().getMaxSize();
        int ttlMinutes = jwtProperties.getCache().getTtlMinutes();
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();

        log.info("TokenValidationStore initialized - maxSize: {}, ttl: {} minutes", maxSize, ttlMinutes);
    }

    @Override
    public Optional<ValidationResult> get(String token) {
        if (!cacheEnabled || cache == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(hashToken(token)));
    }

    @Override
    public void put(String token, ValidationResult result) {
        if (!cacheEnabled || cache == null) {
            return;
        }
        cache.put(hashToken(token), result);
    }

    @Override
    public void invalidate(String token) {
        if (!cacheEnabled || cache == null) {
            return;
        }
        cache.invalidate(hashToken(token));
    }

    @Override
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
        cache.cleanUp();
        var stats = cache.stats();
        return CacheStats.builder()
                .hitCount(stats.hitCount())
                .missCount(stats.missCount())
                .hitRate(stats.hitRate())
                .evictionCount(stats.evictionCount())
                .size(cache.estimatedSize())
                .build();
    }

    private String hashToken(String token) {
        MessageDigest digest = SHA256_DIGEST.get();
        digest.reset();
        byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
