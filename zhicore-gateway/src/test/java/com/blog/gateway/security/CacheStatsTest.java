package com.zhicore.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheStats 单元测试
 * 测试缓存统计数据模型的创建、序列化和字段访问
 */
class CacheStatsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testCacheStatsCreation() {
        // 创建缓存统计
        CacheStats stats = CacheStats.builder()
                .hitCount(80L)
                .missCount(20L)
                .hitRate(0.8)
                .evictionCount(0L)
                .size(100L)
                .build();

        // 验证字段
        assertEquals(80L, stats.getHitCount());
        assertEquals(20L, stats.getMissCount());
        assertEquals(0.8, stats.getHitRate(), 0.001);
        assertEquals(0L, stats.getEvictionCount());
        assertEquals(100L, stats.getSize());
    }

    @Test
    void testZeroStats() {
        // 测试零值统计
        CacheStats stats = CacheStats.builder()
                .hitCount(0L)
                .missCount(0L)
                .hitRate(0.0)
                .evictionCount(0L)
                .size(0L)
                .build();

        assertEquals(0L, stats.getHitCount());
        assertEquals(0L, stats.getMissCount());
        assertEquals(0.0, stats.getHitRate(), 0.001);
    }

    @Test
    void testHighHitRate() {
        // 测试高命中率
        CacheStats stats = CacheStats.builder()
                .hitCount(990L)
                .missCount(10L)
                .hitRate(0.99)
                .evictionCount(0L)
                .size(1000L)
                .build();

        assertEquals(990L, stats.getHitCount());
        assertEquals(10L, stats.getMissCount());
        assertEquals(0.99, stats.getHitRate(), 0.001);
    }

    @Test
    void testLowHitRate() {
        // 测试低命中率
        CacheStats stats = CacheStats.builder()
                .hitCount(5L)
                .missCount(95L)
                .hitRate(0.05)
                .evictionCount(0L)
                .size(100L)
                .build();

        assertEquals(5L, stats.getHitCount());
        assertEquals(95L, stats.getMissCount());
        assertEquals(0.05, stats.getHitRate(), 0.001);
    }

    @Test
    void testJsonSerialization() throws Exception {
        // 创建缓存统计
        CacheStats original = CacheStats.builder()
                .hitCount(400L)
                .missCount(100L)
                .hitRate(0.8)
                .evictionCount(5L)
                .size(500L)
                .build();

        // 序列化为 JSON
        String json = objectMapper.writeValueAsString(original);

        // 反序列化
        CacheStats deserialized = objectMapper.readValue(json, CacheStats.class);

        // 验证反序列化结果
        assertEquals(original.getHitCount(), deserialized.getHitCount());
        assertEquals(original.getMissCount(), deserialized.getMissCount());
        assertEquals(original.getHitRate(), deserialized.getHitRate(), 0.001);
    }

    @Test
    void testLargeNumbers() {
        // 测试大数值
        CacheStats stats = CacheStats.builder()
                .hitCount(Long.MAX_VALUE / 2)
                .missCount(Long.MAX_VALUE / 2)
                .hitRate(0.5)
                .evictionCount(1000L)
                .size(Long.MAX_VALUE)
                .build();

        assertEquals(Long.MAX_VALUE / 2, stats.getHitCount());
        assertEquals(Long.MAX_VALUE / 2, stats.getMissCount());
        assertEquals(0.5, stats.getHitRate(), 0.001);
    }

    @Test
    void testHitRatePrecision() {
        // 测试命中率精度
        CacheStats stats = CacheStats.builder()
                .hitCount(667L)
                .missCount(333L)
                .hitRate(0.667)
                .evictionCount(0L)
                .size(1000L)
                .build();

        assertEquals(0.667, stats.getHitRate(), 0.001);
    }
}
