package com.zhicore.gateway.infrastructure.cache;

import com.zhicore.gateway.config.JwtProperties;
import com.zhicore.gateway.security.ValidationResult;
import com.zhicore.gateway.service.store.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineTokenValidationStoreTest {

    private CaffeineTokenValidationStore store;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        JwtProperties.CacheConfig cacheConfig = new JwtProperties.CacheConfig();
        cacheConfig.setEnabled(true);
        cacheConfig.setMaxSize(100);
        cacheConfig.setTtlMinutes(5);
        jwtProperties.setCache(cacheConfig);
    }

    @Test
    void testCacheHit() {
        store = new CaffeineTokenValidationStore(jwtProperties);

        String token = "test.jwt.token";
        ValidationResult result = ValidationResult.builder()
                .userId("user1")
                .userName("testuser")
                .roles("ROLE_USER")
                .build();

        store.put(token, result);
        Optional<ValidationResult> cached = store.get(token);

        assertTrue(cached.isPresent());
        assertEquals("user1", cached.get().getUserId());
        assertEquals("testuser", cached.get().getUserName());

        CacheStats stats = store.getStats();
        assertEquals(1, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(1.0, stats.getHitRate(), 0.001);
    }

    @Test
    void testCacheMiss() {
        store = new CaffeineTokenValidationStore(jwtProperties);

        Optional<ValidationResult> cached = store.get("nonexistent.token");

        assertFalse(cached.isPresent());

        CacheStats stats = store.getStats();
        assertEquals(0, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(0.0, stats.getHitRate(), 0.001);
    }

    @Test
    void testCacheExpirationConfiguration() {
        JwtProperties.CacheConfig shortTtlConfig = new JwtProperties.CacheConfig();
        shortTtlConfig.setEnabled(true);
        shortTtlConfig.setMaxSize(100);
        shortTtlConfig.setTtlMinutes(1);
        jwtProperties.setCache(shortTtlConfig);

        store = new CaffeineTokenValidationStore(jwtProperties);

        String token = "expiring.token";
        ValidationResult result = ValidationResult.builder()
                .userId("user2")
                .userName("expuser")
                .roles("ROLE_USER")
                .build();
        store.put(token, result);

        Optional<ValidationResult> cached = store.get(token);
        assertTrue(cached.isPresent());
        assertEquals("user2", cached.get().getUserId());
        assertEquals(1, store.getStats().getHitCount());
    }

    @Test
    void testCacheEviction() {
        JwtProperties.CacheConfig smallCacheConfig = new JwtProperties.CacheConfig();
        smallCacheConfig.setEnabled(true);
        smallCacheConfig.setMaxSize(3);
        smallCacheConfig.setTtlMinutes(5);
        jwtProperties.setCache(smallCacheConfig);

        store = new CaffeineTokenValidationStore(jwtProperties);

        for (int i = 1; i <= 4; i++) {
            store.put("token" + i, ValidationResult.builder()
                    .userId("user" + i)
                    .userName("user" + i)
                    .roles("ROLE_USER")
                    .build());
        }

        CacheStats stats = store.getStats();
        assertTrue(stats.getSize() <= 3);
    }

    @Test
    void testCacheInvalidate() {
        store = new CaffeineTokenValidationStore(jwtProperties);

        String token = "invalidate.token";
        ValidationResult result = ValidationResult.builder()
                .userId("user3")
                .userName("invuser")
                .roles("ROLE_USER")
                .build();
        store.put(token, result);

        assertTrue(store.get(token).isPresent());
        store.invalidate(token);
        assertFalse(store.get(token).isPresent());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        store = new CaffeineTokenValidationStore(jwtProperties);

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String token = "thread.token." + index;
                    ValidationResult result = ValidationResult.builder()
                            .userId("user" + index)
                            .userName("user" + index)
                            .roles("ROLE_USER")
                            .build();
                    store.put(token, result);
                    Optional<ValidationResult> cached = store.get(token);
                    if (cached.isPresent() && cached.get().getUserId().equals("user" + index)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, successCount.get());
    }

    @Test
    void testCacheDisabled() {
        JwtProperties.CacheConfig disabledConfig = new JwtProperties.CacheConfig();
        disabledConfig.setEnabled(false);
        disabledConfig.setMaxSize(100);
        disabledConfig.setTtlMinutes(5);
        jwtProperties.setCache(disabledConfig);

        store = new CaffeineTokenValidationStore(jwtProperties);

        store.put("disabled.token", ValidationResult.builder()
                .userId("user4")
                .userName("disuser")
                .roles("ROLE_USER")
                .build());

        assertFalse(store.get("disabled.token").isPresent());
        CacheStats stats = store.getStats();
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0.0, stats.getHitRate(), 0.001);
    }

    @Test
    void testCacheDisabledNoException() {
        JwtProperties.CacheConfig disabledConfig = new JwtProperties.CacheConfig();
        disabledConfig.setEnabled(false);
        jwtProperties.setCache(disabledConfig);

        store = new CaffeineTokenValidationStore(jwtProperties);

        assertDoesNotThrow(() -> {
            ValidationResult testResult = ValidationResult.builder()
                    .userId("user1")
                    .userName("user1")
                    .roles("ROLE_USER")
                    .build();
            store.put("token1", testResult);
            store.get("token1");
            store.invalidate("token1");
            store.getStats();
        });
    }

    @Test
    void testMultiplePutSameToken() {
        store = new CaffeineTokenValidationStore(jwtProperties);

        String token = "same.token";
        store.put(token, ValidationResult.builder()
                .userId("user1")
                .userName("user1")
                .roles("ROLE_USER")
                .build());
        store.put(token, ValidationResult.builder()
                .userId("user2")
                .userName("user2")
                .roles("ROLE_ADMIN")
                .build());

        Optional<ValidationResult> cached = store.get(token);
        assertTrue(cached.isPresent());
        assertEquals("user2", cached.get().getUserId());
        assertEquals("ROLE_ADMIN", cached.get().getRoles());
    }

    @Test
    void testHitRateCalculation() {
        store = new CaffeineTokenValidationStore(jwtProperties);

        for (int i = 1; i <= 3; i++) {
            store.put("token" + i, ValidationResult.builder()
                    .userId("user" + i)
                    .userName("user" + i)
                    .roles("ROLE_USER")
                    .build());
        }

        store.get("token1");
        store.get("token2");
        store.get("token4");
        store.get("token5");
        store.get("token6");

        CacheStats stats = store.getStats();
        assertEquals(2, stats.getHitCount());
        assertEquals(3, stats.getMissCount());
        assertEquals(0.4, stats.getHitRate(), 0.001);
    }
}
