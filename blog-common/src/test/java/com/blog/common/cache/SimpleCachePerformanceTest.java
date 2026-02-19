package com.blog.common.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 简化的缓存性能测试
 * 
 * Feature: cache-penetration-protection
 * Validates: Requirements 13.5
 */
public class SimpleCachePerformanceTest {

    private RedissonClient redissonClient;
    private RedisTemplate<String, Object> redisTemplate;
    private static final String TEST_CACHE_KEY = "perf:cache:test";
    private static final String TEST_LOCK_KEY = "perf:lock:test";
    private static final int ITERATIONS = 1000;

    @BeforeEach
    public void setup() {
        // 初始化 Redisson
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setPassword("redis123456")
                .setDatabase(0);
        redissonClient = Redisson.create(config);

        // 初始化 RedisTemplate
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName("localhost");
        redisConfig.setPort(6379);
        redisConfig.setPassword("redis123456");
        redisConfig.setDatabase(0);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();

        // 预热缓存
        redisTemplate.opsForValue().set(TEST_CACHE_KEY, "test-value", 60, TimeUnit.SECONDS);
    }

    @AfterEach
    public void tearDown() {
        redisTemplate.delete(TEST_CACHE_KEY);
        redisTemplate.delete(TEST_LOCK_KEY);
        
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    public void testCacheHitPerformance() {
        System.out.println("\n========================================");
        System.out.println("Cache Hit Performance Test");
        System.out.println("========================================\n");
        
        List<Long> times = new ArrayList<>();
        
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            Object value = redisTemplate.opsForValue().get(TEST_CACHE_KEY);
            long end = System.nanoTime();
            times.add((end - start) / 1000); // 转换为微秒
        }
        
        printStatistics("Cache Hit (No Lock)", times);
    }

    @Test
    public void testLockAcquireReleasePerformance() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("Lock Acquire/Release Performance Test");
        System.out.println("========================================\n");
        
        List<Long> times = new ArrayList<>();
        
        for (int i = 0; i < ITERATIONS; i++) {
            RLock lock = redissonClient.getLock(TEST_LOCK_KEY + ":" + i);
            long start = System.nanoTime();
            try {
                boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
                if (acquired) {
                    // 模拟业务逻辑
                    Thread.sleep(1);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            long end = System.nanoTime();
            times.add((end - start) / 1000); // 转换为微秒
        }
        
        printStatistics("Lock Acquire/Release", times);
    }

    @Test
    public void testCacheMissWithLockPerformance() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("Cache Miss with Lock Performance Test");
        System.out.println("========================================\n");
        
        List<Long> times = new ArrayList<>();
        
        for (int i = 0; i < ITERATIONS; i++) {
            String missKey = TEST_CACHE_KEY + ":miss:" + i;
            String lockKey = TEST_LOCK_KEY + ":miss:" + i;
            RLock lock = redissonClient.getLock(lockKey);
            
            long start = System.nanoTime();
            try {
                // 第一次检查缓存
                Object cached = redisTemplate.opsForValue().get(missKey);
                if (cached == null) {
                    // 获取锁
                    boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
                    if (acquired) {
                        try {
                            // DCL 双重检查
                            cached = redisTemplate.opsForValue().get(missKey);
                            if (cached == null) {
                                // 模拟数据库查询
                                Thread.sleep(10);
                                String data = "loaded-data";
                                // 写入缓存
                                redisTemplate.opsForValue().set(missKey, data, 60, TimeUnit.SECONDS);
                            }
                        } finally {
                            if (lock.isHeldByCurrentThread()) {
                                lock.unlock();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            long end = System.nanoTime();
            times.add((end - start) / 1000); // 转换为微秒
        }
        
        printStatistics("Cache Miss with Lock", times);
    }

    @Test
    public void testFairVsUnfairLock() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("Fair vs Unfair Lock Performance Test");
        System.out.println("========================================\n");
        
        // 测试非公平锁
        List<Long> unfairTimes = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            RLock lock = redissonClient.getLock(TEST_LOCK_KEY + ":unfair:" + i);
            long start = System.nanoTime();
            try {
                boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
                if (acquired) {
                    Thread.sleep(1);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            long end = System.nanoTime();
            unfairTimes.add((end - start) / 1000);
        }
        
        // 测试公平锁
        List<Long> fairTimes = new ArrayList<>();
        for (int i = 0; i < ITERATIONS; i++) {
            RLock lock = redissonClient.getFairLock(TEST_LOCK_KEY + ":fair:" + i);
            long start = System.nanoTime();
            try {
                boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
                if (acquired) {
                    Thread.sleep(1);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            long end = System.nanoTime();
            fairTimes.add((end - start) / 1000);
        }
        
        printStatistics("Unfair Lock", unfairTimes);
        printStatistics("Fair Lock", fairTimes);
        
        double unfairAvg = unfairTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double fairAvg = fairTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double overhead = ((fairAvg - unfairAvg) / unfairAvg) * 100;
        
        System.out.println("\nFair Lock Overhead: " + String.format("%.2f%%", overhead));
    }

    private void printStatistics(String testName, List<Long> times) {
        times.sort(Long::compareTo);
        
        long min = times.get(0);
        long max = times.get(times.size() - 1);
        double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);
        long p50 = times.get(times.size() / 2);
        long p95 = times.get((int) (times.size() * 0.95));
        long p99 = times.get((int) (times.size() * 0.99));
        
        System.out.println(testName + " Statistics (microseconds):");
        System.out.println("  Iterations: " + times.size());
        System.out.println("  Min: " + min + " μs");
        System.out.println("  Max: " + max + " μs");
        System.out.println("  Avg: " + String.format("%.2f", avg) + " μs");
        System.out.println("  P50: " + p50 + " μs");
        System.out.println("  P95: " + p95 + " μs");
        System.out.println("  P99: " + p99 + " μs");
        System.out.println();
    }
}
