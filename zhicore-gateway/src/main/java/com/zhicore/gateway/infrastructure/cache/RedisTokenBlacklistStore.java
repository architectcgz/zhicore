package com.zhicore.gateway.infrastructure.cache;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.gateway.application.port.store.TokenBlacklistStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 基于 Redis 的 token 黑名单存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklistStore implements TokenBlacklistStore {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Boolean> isBlacklisted(String token) {
        return redisTemplate.hasKey(buildBlacklistKey(token));
    }

    @Override
    public Mono<Boolean> addToBlacklist(String token, Duration ttl) {
        return redisTemplate.opsForValue().set(buildBlacklistKey(token), "1", ttl);
    }

    private String buildBlacklistKey(String token) {
        return CacheConstants.withNamespace("token") + ":blacklist:" + hashToken(token);
    }

    /**
     * 避免直接将完整 JWT 落到 Redis key。
     */
    private String hashToken(String token) {
        if (token.length() > 32) {
            return token.substring(token.length() - 32);
        }
        return token;
    }
}
