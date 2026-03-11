package com.zhicore.gateway.application.port.store;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Token 黑名单存储抽象。
 */
public interface TokenBlacklistStore {

    /**
     * 检查 token 是否已加入黑名单。
     */
    Mono<Boolean> isBlacklisted(String token);

    /**
     * 将 token 加入黑名单并设置过期时间。
     */
    Mono<Boolean> addToBlacklist(String token, Duration ttl);
}
