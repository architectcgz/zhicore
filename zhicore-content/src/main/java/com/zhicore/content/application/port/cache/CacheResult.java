package com.zhicore.content.application.port.cache;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缓存三态结果（R11）
 *
 * - MISS：Key 不存在（未命中）
 * - NULL：Key 存在，但值为“空值标记”（防穿透）
 * - HIT：Key 存在，且包含实际值
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CacheResult<T> {

    private final CacheResultType type;
    private final T value;

    public boolean isMiss() {
        return type == CacheResultType.MISS;
    }

    public boolean isNull() {
        return type == CacheResultType.NULL;
    }

    public boolean isHit() {
        return type == CacheResultType.HIT;
    }

    public static <T> CacheResult<T> miss() {
        return new CacheResult<>(CacheResultType.MISS, null);
    }

    public static <T> CacheResult<T> nullValue() {
        return new CacheResult<>(CacheResultType.NULL, null);
    }

    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(CacheResultType.HIT, value);
    }
}

