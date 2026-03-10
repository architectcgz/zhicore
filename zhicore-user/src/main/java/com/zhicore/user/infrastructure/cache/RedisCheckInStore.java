package com.zhicore.user.infrastructure.cache;

import com.zhicore.user.application.port.store.CheckInStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 基于 Redis Bitmap 的签到缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisCheckInStore implements CheckInStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean hasCheckedIn(Long userId, LocalDate date) {
        String key = UserRedisKeys.checkInBitmap(userId, YearMonth.from(date));
        int bitOffset = date.getDayOfMonth() - 1;
        Boolean bit = redisTemplate.opsForValue().getBit(key, bitOffset);
        return Boolean.TRUE.equals(bit);
    }

    @Override
    public void markCheckedIn(Long userId, LocalDate date) {
        String key = UserRedisKeys.checkInBitmap(userId, YearMonth.from(date));
        int bitOffset = date.getDayOfMonth() - 1;
        redisTemplate.opsForValue().setBit(key, bitOffset, true);
    }

    @Override
    public long getMonthlyBitmap(Long userId, YearMonth yearMonth) {
        String key = UserRedisKeys.checkInBitmap(userId, yearMonth);
        byte[] bytes = (byte[]) redisTemplate.execute(connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)), true);
        if (bytes == null || bytes.length == 0) {
            return 0L;
        }

        long bitmap = 0L;
        for (int i = 0; i < bytes.length && i < Long.BYTES; i++) {
            bitmap |= ((long) (bytes[i] & 0xFF)) << (i * Byte.SIZE);
        }
        return bitmap;
    }
}
