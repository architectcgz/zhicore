package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存失效集成测试（R13）
 *
 * 覆盖：SCAN 失效、批量删除、精确删除优先
 */
@DisplayName("缓存失效集成测试")
class CacheInvalidationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CacheStore cacheStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        cleanupRedis();
    }

    // ==================== 10.1 SCAN 失效测试 ====================

    @Nested
    @DisplayName("SCAN 模式失效")
    class ScanInvalidationTests {

        @Test
        @DisplayName("deletePattern 使用通配符能删除匹配的 key")
        void deletePatternShouldRemoveMatchingKeys() {
            // 写入多个匹配 key
            cacheStore.set("test:list:v2:tag:1:page:1", "a", Duration.ofMinutes(5));
            cacheStore.set("test:list:v2:tag:1:page:2", "b", Duration.ofMinutes(5));
            cacheStore.set("test:list:v2:tag:1:page:3", "c", Duration.ofMinutes(5));
            // 不匹配的 key
            cacheStore.set("test:list:v2:tag:2:page:1", "d", Duration.ofMinutes(5));

            cacheStore.deletePattern("test:list:v2:tag:1:*");

            // 匹配的 key 应被删除
            assertThat(cacheStore.get("test:list:v2:tag:1:page:1", String.class).isMiss()).isTrue();
            assertThat(cacheStore.get("test:list:v2:tag:1:page:2", String.class).isMiss()).isTrue();
            assertThat(cacheStore.get("test:list:v2:tag:1:page:3", String.class).isMiss()).isTrue();
            // 不匹配的 key 应保留
            assertThat(cacheStore.get("test:list:v2:tag:2:page:1", String.class).isHit()).isTrue();
        }

        @Test
        @DisplayName("deletePattern 空 pattern 不执行任何操作")
        void deletePatternWithEmptyPatternShouldDoNothing() {
            cacheStore.set("safe:key", "value", Duration.ofMinutes(5));

            cacheStore.deletePattern(null);
            cacheStore.deletePattern("");
            cacheStore.deletePattern("   ");

            assertThat(cacheStore.get("safe:key", String.class).isHit()).isTrue();
        }
    }

    // ==================== 10.2 批量删除性能测试 ====================

    @Nested
    @DisplayName("批量删除")
    class BatchDeleteTests {

        @Test
        @DisplayName("大量 key 通过 SCAN 批量删除")
        void scanShouldDeleteLargeNumberOfKeys() {
            // 写入超过默认 scanBatchSize(100) 的 key
            for (int i = 0; i < 150; i++) {
                stringRedisTemplate.opsForValue().set(
                        "batch:test:key:" + i, "val" + i, Duration.ofMinutes(5));
            }

            cacheStore.deletePattern("batch:test:key:*");

            // 所有 key 应被删除
            Set<String> remaining = stringRedisTemplate.keys("batch:test:key:*");
            assertThat(remaining).isEmpty();
        }

        @Test
        @DisplayName("精确 delete 支持多 key 批量删除")
        void deleteShouldSupportMultipleKeys() {
            cacheStore.set("multi:a", "1", Duration.ofMinutes(5));
            cacheStore.set("multi:b", "2", Duration.ofMinutes(5));
            cacheStore.set("multi:c", "3", Duration.ofMinutes(5));

            cacheStore.delete("multi:a", "multi:b", "multi:c");

            assertThat(cacheStore.get("multi:a", String.class).isMiss()).isTrue();
            assertThat(cacheStore.get("multi:b", String.class).isMiss()).isTrue();
            assertThat(cacheStore.get("multi:c", String.class).isMiss()).isTrue();
        }
    }

    // ==================== 10.3 精确删除优先测试 ====================

    @Nested
    @DisplayName("精确删除优先")
    class PreciseDeleteTests {

        @Test
        @DisplayName("无通配符的 pattern 按精确 key 删除")
        void patternWithoutWildcardShouldDeleteExactKey() {
            cacheStore.set("exact:key:123", "value", Duration.ofMinutes(5));

            // 无通配符，走精确删除路径
            cacheStore.deletePattern("exact:key:123");

            assertThat(cacheStore.get("exact:key:123", String.class).isMiss()).isTrue();
        }

        @Test
        @DisplayName("精确删除不影响其他 key")
        void preciseDeleteShouldNotAffectOtherKeys() {
            cacheStore.set("precise:a", "1", Duration.ofMinutes(5));
            cacheStore.set("precise:b", "2", Duration.ofMinutes(5));

            cacheStore.deletePattern("precise:a");

            assertThat(cacheStore.get("precise:a", String.class).isMiss()).isTrue();
            assertThat(cacheStore.get("precise:b", String.class).isHit()).isTrue();
        }
    }
}