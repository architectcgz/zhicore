package com.blog.common.cache;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

/**
 * 缓存击穿防护性能基准测试
 * 
 * 测试目标：
 * 1. 测量锁获取和释放的耗时
 * 2. 对比加锁前后的性能差异
 * 3. 测量缓存命中率对性能的影响
 * 
 * Feature: cache-penetration-protection
 * Validates: Requirements 13.5
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CacheLockBenchmark {

    private RedissonClient redissonClient;
    private RedisTemplate<String, Object> redisTemplate;
    private static final String TEST_CACHE_KEY = "benchmark:cache:test";
    private static final String TEST_LOCK_KEY = "benchmark:lock:test";
    private static final Long TEST_ENTITY_ID = 12345L;

    @Setup
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

    @TearDown
    public void tearDown() {
        // 清理测试数据
        redisTemplate.delete(TEST_CACHE_KEY);
        redisTemplate.delete(TEST_LOCK_KEY);
        
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    /**
     * 基准测试：直接从缓存读取（无锁）
     */
    @Benchmark
    public Object benchmarkCacheHitNoLock() {
        return redisTemplate.opsForValue().get(TEST_CACHE_KEY);
    }

    /**
     * 基准测试：缓存命中 + 锁检查（模拟热点数据识别）
     */
    @Benchmark
    public Object benchmarkCacheHitWithLockCheck() {
        // 模拟热点数据识别
        boolean isHot = true;
        
        if (isHot) {
            // 第一次检查缓存
            Object cached = redisTemplate.opsForValue().get(TEST_CACHE_KEY);
            if (cached != null) {
                return cached;
            }
        }
        
        return redisTemplate.opsForValue().get(TEST_CACHE_KEY);
    }

    /**
     * 基准测试：锁获取和释放（无竞争）
     */
    @Benchmark
    public void benchmarkLockAcquireRelease() throws InterruptedException {
        RLock lock = redissonClient.getLock(TEST_LOCK_KEY);
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
    }

    /**
     * 基准测试：完整的缓存未命中流程（带锁）
     */
    @Benchmark
    public Object benchmarkCacheMissWithLock() throws InterruptedException {
        String missKey = TEST_CACHE_KEY + ":miss";
        RLock lock = redissonClient.getLock(TEST_LOCK_KEY + ":miss");
        
        try {
            // 第一次检查缓存
            Object cached = redisTemplate.opsForValue().get(missKey);
            if (cached != null) {
                return cached;
            }

            // 获取锁
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!acquired) {
                return null;
            }

            try {
                // DCL 双重检查
                cached = redisTemplate.opsForValue().get(missKey);
                if (cached != null) {
                    return cached;
                }

                // 模拟数据库查询
                Thread.sleep(10);
                String data = "loaded-data";

                // 写入缓存
                redisTemplate.opsForValue().set(missKey, data, 60, TimeUnit.SECONDS);
                return data;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 基准测试：公平锁 vs 非公平锁
     */
    @Benchmark
    public void benchmarkFairLock() throws InterruptedException {
        RLock lock = redissonClient.getFairLock(TEST_LOCK_KEY + ":fair");
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
    }

    /**
     * 基准测试：非公平锁
     */
    @Benchmark
    public void benchmarkUnfairLock() throws InterruptedException {
        RLock lock = redissonClient.getLock(TEST_LOCK_KEY + ":unfair");
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
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CacheLockBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
