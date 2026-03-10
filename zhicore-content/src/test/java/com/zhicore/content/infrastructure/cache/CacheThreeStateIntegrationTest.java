package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存三态集成测试（R11）
 *
 * 覆盖：MISS、NULL、HIT 状态、NULL TTL、缓存失效后重建
 */
@DisplayName("缓存三态集成测试")
class CacheThreeStateIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        cleanupRedis();
    }

    // ==================== 8.1 MISS 状态测试 ====================

    @Nested
    @DisplayName("MISS 状态")
    class MissTests {

        @Test
        @DisplayName("首次查询缓存不存在返回 MISS")
        void shouldReturnMissWhenKeyNotExists() {
            CacheResult<String> result = cacheStore.get("miss:key", String.class);

            assertThat(result.isMiss()).isTrue();
            assertThat(result.isHit()).isFalse();
            assertThat(result.isNull()).isFalse();
            assertThat(result.getValue()).isNull();
        }
    }

    // ==================== 8.2 NULL 状态测试 ====================

    @Nested
    @DisplayName("NULL 状态")
    class NullTests {

        @Test
        @DisplayName("缓存 NULL 标记后返回 NULL 状态")
        void shouldReturnNullWhenNullMarkerCached() {
            cacheStore.set("null:key", null, Duration.ofMinutes(5));

            CacheResult<String> result = cacheStore.get("null:key", String.class);

            assertThat(result.isNull()).isTrue();
            assertThat(result.isMiss()).isFalse();
            assertThat(result.isHit()).isFalse();
            assertThat(result.getValue()).isNull();
        }

        @Test
        @DisplayName("NULL 标记的 Redis 存储格式包含 type=NULL")
        void nullMarkerShouldContainTypeNull() {
            cacheStore.set("null:format", null, Duration.ofMinutes(5));

            String raw = stringRedisTemplate.opsForValue().get("null:format");
            assertThat(raw).isNotNull();
            assertThat(raw).contains("\"type\"");
            assertThat(raw).contains("NULL");
            // NULL payload 不应包含 value 字段
            assertThat(raw).doesNotContain("\"value\"");
        }
    }

    // ==================== 8.3 HIT 状态测试 ====================

    @Nested
    @DisplayName("HIT 状态")
    class HitTests {

        @Test
        @DisplayName("缓存命中返回 HIT 和正确值")
        void shouldReturnHitWithCorrectValue() {
            cacheStore.set("hit:key", "hello", Duration.ofMinutes(30));

            CacheResult<String> result = cacheStore.get("hit:key", String.class);

            assertThat(result.isHit()).isTrue();
            assertThat(result.isMiss()).isFalse();
            assertThat(result.isNull()).isFalse();
            assertThat(result.getValue()).isEqualTo("hello");
        }

        @Test
        @DisplayName("HIT 的 Redis 存储格式包含 type=HIT 和 value")
        void hitPayloadShouldContainTypeAndValue() {
            cacheStore.set("hit:format", "data", Duration.ofMinutes(30));

            String raw = stringRedisTemplate.opsForValue().get("hit:format");
            assertThat(raw).isNotNull();
            assertThat(raw).contains("\"type\"");
            assertThat(raw).contains("HIT");
            assertThat(raw).contains("\"value\"");
        }
    }

    // ==================== 8.4 NULL TTL 测试 ====================

    @Nested
    @DisplayName("TTL 测试")
    class TtlTests {

        @Test
        @DisplayName("NULL 标记和正常缓存可设置不同 TTL")
        void nullAndHitCanHaveDifferentTtl() {
            cacheStore.set("ttl:null", null, Duration.ofMinutes(5));
            cacheStore.set("ttl:hit", "value", Duration.ofMinutes(30));

            Long nullTtl = stringRedisTemplate.getExpire("ttl:null", TimeUnit.SECONDS);
            Long hitTtl = stringRedisTemplate.getExpire("ttl:hit", TimeUnit.SECONDS);

            assertThat(nullTtl).isNotNull().isPositive();
            assertThat(hitTtl).isNotNull().isPositive();
            // NULL TTL 应小于 HIT TTL
            assertThat(nullTtl).isLessThan(hitTtl);
        }
    }

    // ==================== 8.5 缓存失效后重建测试 ====================

    @Nested
    @DisplayName("缓存失效后重建")
    class InvalidationRebuildTests {

        @Test
        @DisplayName("删除缓存后再次查询返回 MISS")
        void deleteShouldCauseMiss() {
            cacheStore.set("rebuild:key", "old", Duration.ofMinutes(30));
            assertThat(cacheStore.get("rebuild:key", String.class).isHit()).isTrue();

            cacheStore.delete("rebuild:key");

            CacheResult<String> result = cacheStore.get("rebuild:key", String.class);
            assertThat(result.isMiss()).isTrue();
        }

        @Test
        @DisplayName("NULL 标记被删除后可重新写入实际值")
        void nullMarkerCanBeReplacedWithRealValue() {
            // 先缓存 NULL
            cacheStore.set("rebuild:null", null, Duration.ofMinutes(5));
            assertThat(cacheStore.get("rebuild:null", String.class).isNull()).isTrue();

            // 删除 NULL 标记
            cacheStore.delete("rebuild:null");

            // 写入实际值
            cacheStore.set("rebuild:null", "new-data", Duration.ofMinutes(30));

            CacheResult<String> result = cacheStore.get("rebuild:null", String.class);
            assertThat(result.isHit()).isTrue();
            assertThat(result.getValue()).isEqualTo("new-data");
        }
    }
}
