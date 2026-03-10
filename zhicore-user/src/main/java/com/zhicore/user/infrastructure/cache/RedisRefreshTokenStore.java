package com.zhicore.user.infrastructure.cache;

import com.zhicore.user.application.port.store.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 Refresh Token 白名单存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final int SCAN_COUNT = 100;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean exists(Long userId, String tokenId) {
        Boolean exists = redisTemplate.hasKey(UserRedisKeys.refreshTokenWhitelist(userId, tokenId));
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void store(Long userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(
                UserRedisKeys.refreshTokenWhitelist(userId, tokenId),
                "1",
                ttl
        );
    }

    @Override
    public void revoke(Long userId, String tokenId) {
        redisTemplate.delete(UserRedisKeys.refreshTokenWhitelist(userId, tokenId));
    }

    @Override
    public long revokeAll(Long userId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(UserRedisKeys.refreshTokenPattern(userId))
                .count(SCAN_COUNT)
                .build();
        long deleted = 0L;
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
                deleted++;
            }
        }
        return deleted;
    }
}
