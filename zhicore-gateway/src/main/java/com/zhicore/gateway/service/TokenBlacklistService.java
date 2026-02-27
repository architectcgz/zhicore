package com.zhicore.gateway.service;

import com.zhicore.common.cache.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Token 黑名单服务
 * 
 * 用于存储已注销或被撤销的 Token
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static String blacklistKeyPrefix() {
        return CacheConstants.withNamespace("token") + ":blacklist:";
    }

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中
     */
    public Mono<Boolean> isBlacklisted(String token) {
        String key = blacklistKeyPrefix() + hashToken(token);
        return redisTemplate.hasKey(key)
                .onErrorResume(e -> {
                    log.error("Failed to check token blacklist", e);
                    // Redis 不可用时，默认允许通过
                    return Mono.just(false);
                });
    }

    /**
     * 将 Token 加入黑名单
     *
     * @param token JWT Token
     * @param ttl 过期时间
     * @return 操作结果
     */
    public Mono<Boolean> addToBlacklist(String token, Duration ttl) {
        String key = blacklistKeyPrefix() + hashToken(token);
        return redisTemplate.opsForValue()
                .set(key, "1", ttl)
                .doOnSuccess(result -> log.info("Token added to blacklist"))
                .onErrorResume(e -> {
                    log.error("Failed to add token to blacklist", e);
                    return Mono.just(false);
                });
    }

    /**
     * 对 Token 进行哈希处理（避免存储完整 Token）
     */
    private String hashToken(String token) {
        // 使用 Token 的后32位作为标识（JWT 的签名部分）
        if (token.length() > 32) {
            return token.substring(token.length() - 32);
        }
        return token;
    }
}
