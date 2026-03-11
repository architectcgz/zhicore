package com.zhicore.gateway.application.port.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheStatsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testCacheStatsCreation() {
        CacheStats stats = CacheStats.builder()
                .hitCount(80L)
                .missCount(20L)
                .hitRate(0.8)
                .evictionCount(0L)
                .size(100L)
                .build();

        assertEquals(80L, stats.getHitCount());
        assertEquals(20L, stats.getMissCount());
        assertEquals(0.8, stats.getHitRate(), 0.001);
        assertEquals(0L, stats.getEvictionCount());
        assertEquals(100L, stats.getSize());
    }

    @Test
    void testJsonSerialization() throws Exception {
        CacheStats original = CacheStats.builder()
                .hitCount(400L)
                .missCount(100L)
                .hitRate(0.8)
                .evictionCount(5L)
                .size(500L)
                .build();

        String json = objectMapper.writeValueAsString(original);
        CacheStats deserialized = objectMapper.readValue(json, CacheStats.class);

        assertEquals(original.getHitCount(), deserialized.getHitCount());
        assertEquals(original.getMissCount(), deserialized.getMissCount());
        assertEquals(original.getHitRate(), deserialized.getHitRate(), 0.001);
        assertEquals(original.getEvictionCount(), deserialized.getEvictionCount());
        assertEquals(original.getSize(), deserialized.getSize());
    }
}
