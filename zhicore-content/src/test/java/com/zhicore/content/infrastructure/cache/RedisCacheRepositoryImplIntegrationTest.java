package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.cache.CacheResult;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheRepositoryImplIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CacheRepository cacheRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        cleanupRedis();
    }

    @Test
    void getShouldReturnMissWhenKeyNotExists() {
        CacheResult<String> result = cacheRepository.get("k:miss", String.class);
        assertThat(result.isMiss()).isTrue();
    }

    @Test
    void getShouldReturnNullWhenNullPayloadCached() {
        cacheRepository.set("k:null", null, Duration.ofMinutes(5));
        CacheResult<String> result = cacheRepository.get("k:null", String.class);
        assertThat(result.isNull()).isTrue();
    }

    @Test
    void getShouldReturnHitWhenValueCached() {
        cacheRepository.set("k:hit", "v1", Duration.ofMinutes(5));
        CacheResult<String> result = cacheRepository.get("k:hit", String.class);
        assertThat(result.isHit()).isTrue();
        assertThat(result.getValue()).isEqualTo("v1");
    }

    @Test
    void deletePatternShouldDeleteKeysViaScan() {
        stringRedisTemplate.opsForValue().set("scan:test:1", "a");
        stringRedisTemplate.opsForValue().set("scan:test:2", "b");
        stringRedisTemplate.opsForValue().set("scan:other:1", "c");

        cacheRepository.deletePattern("scan:test:*");

        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey("scan:test:1"))).isFalse();
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey("scan:test:2"))).isFalse();
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey("scan:other:1"))).isTrue();
    }
}

