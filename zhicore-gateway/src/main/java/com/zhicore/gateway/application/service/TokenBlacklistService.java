package com.zhicore.gateway.application.service;

import com.zhicore.gateway.application.port.store.TokenBlacklistStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final TokenBlacklistStore tokenBlacklistStore;

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中
     */
    public Mono<Boolean> isBlacklisted(String token) {
        return tokenBlacklistStore.isBlacklisted(token)
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
        return tokenBlacklistStore.addToBlacklist(token, ttl)
                .doOnSuccess(result -> log.info("Token added to blacklist"))
                .onErrorResume(e -> {
                    log.error("Failed to add token to blacklist", e);
                    return Mono.just(false);
                });
    }
}
