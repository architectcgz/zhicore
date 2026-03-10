package com.zhicore.user.application.port.store;

import java.time.Duration;

/**
 * Refresh Token 白名单存储端口。
 *
 * 封装 token 白名单的缓存读写与批量吊销逻辑，
 * 避免应用层直接依赖 RedisTemplate 和扫描细节。
 */
public interface RefreshTokenStore {

    /**
     * 检查指定 Refresh Token 是否仍在白名单内。
     *
     * @param userId 用户ID
     * @param tokenId Refresh Token 标识
     * @return 是否存在
     */
    boolean exists(Long userId, String tokenId);

    /**
     * 保存 Refresh Token 白名单记录。
     *
     * @param userId 用户ID
     * @param tokenId Refresh Token 标识
     * @param ttl 过期时间
     */
    void store(Long userId, String tokenId, Duration ttl);

    /**
     * 吊销单个 Refresh Token。
     *
     * @param userId 用户ID
     * @param tokenId Refresh Token 标识
     */
    void revoke(Long userId, String tokenId);

    /**
     * 吊销用户全部 Refresh Token。
     *
     * @param userId 用户ID
     * @return 实际吊销数量
     */
    long revokeAll(Long userId);
}
