package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheStoreImplIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        cleanupRedis();
    }

    @Test
    void getShouldReturnMissWhenKeyNotExists() {
        CacheResult<String> result = cacheStore.get("k:miss", String.class);
        assertThat(result.isMiss()).isTrue();
    }

    @Test
    void getShouldReturnNullWhenNullPayloadCached() {
        cacheStore.set("k:null", null, Duration.ofMinutes(5));
        CacheResult<String> result = cacheStore.get("k:null", String.class);
        assertThat(result.isNull()).isTrue();
    }

    @Test
    void getShouldReturnHitWhenValueCached() {
        cacheStore.set("k:hit", "v1", Duration.ofMinutes(5));
        CacheResult<String> result = cacheStore.get("k:hit", String.class);
        assertThat(result.isHit()).isTrue();
        assertThat(result.getValue()).isEqualTo("v1");
    }

    @Test
    void deletePatternShouldDeleteKeysViaScan() {
        stringRedisTemplate.opsForValue().set("scan:test:1", "a");
        stringRedisTemplate.opsForValue().set("scan:test:2", "b");
        stringRedisTemplate.opsForValue().set("scan:other:1", "c");

        cacheStore.deletePattern("scan:test:*");

        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey("scan:test:1"))).isFalse();
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey("scan:test:2"))).isFalse();
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey("scan:other:1"))).isTrue();
    }
}

