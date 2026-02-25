package com.zhicore.gateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtMetricsCollector 单元测试
 * 测试指标记录、计数器递增、计时器记录
 */
class JwtMetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private JwtMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new JwtMetricsCollector(meterRegistry);
    }

    @Test
    void testRecordSuccess() {
        // 记录成功
        metricsCollector.recordSuccess();
        metricsCollector.recordSuccess();
        metricsCollector.recordSuccess();

        // 验证计数器
        Counter counter = meterRegistry.find("jwt.validation.success").counter();
        assertNotNull(counter);
        assertEquals(3.0, counter.count(), 0.001);
    }

    @Test
    void testRecordFailure() {
        // 记录失败
        metricsCollector.recordFailure("SignatureException");
        metricsCollector.recordFailure("MalformedJwtException");
        metricsCollector.recordFailure("SignatureException");

        // 验证计数器
        Counter signatureCounter = meterRegistry.find("jwt.validation.failure")
                .tag("error", "SignatureException")
                .counter();
        assertNotNull(signatureCounter);
        assertEquals(2.0, signatureCounter.count(), 0.001);

        Counter malformedCounter = meterRegistry.find("jwt.validation.failure")
                .tag("error", "MalformedJwtException")
                .counter();
        assertNotNull(malformedCounter);
        assertEquals(1.0, malformedCounter.count(), 0.001);
    }

    @Test
    void testRecordExpired() {
        // 记录过期
        metricsCollector.recordExpired();
        metricsCollector.recordExpired();

        // 验证计数器
        Counter counter = meterRegistry.find("jwt.validation.expired").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count(), 0.001);
    }

    @Test
    void testRecordCacheHit() {
        // 记录缓存命中
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheHit();

        // 验证计数器
        Counter counter = meterRegistry.find("jwt.cache.hit").counter();
        assertNotNull(counter);
        assertEquals(4.0, counter.count(), 0.001);
    }

    @Test
    void testRecordCacheMiss() {
        // 记录缓存未命中
        metricsCollector.recordCacheMiss();
        metricsCollector.recordCacheMiss();

        // 验证计数器
        Counter counter = meterRegistry.find("jwt.cache.miss").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count(), 0.001);
    }

    @Test
    void testRecordValidationTime() {
        // 记录验证时间（纳秒）
        long time1 = TimeUnit.MILLISECONDS.toNanos(10); // 10ms
        long time2 = TimeUnit.MILLISECONDS.toNanos(20); // 20ms
        long time3 = TimeUnit.MILLISECONDS.toNanos(30); // 30ms

        metricsCollector.recordValidationTime(time1);
        metricsCollector.recordValidationTime(time2);
        metricsCollector.recordValidationTime(time3);

        // 验证计时器
        Timer timer = meterRegistry.find("jwt.validation.time").timer();
        assertNotNull(timer);
        assertEquals(3, timer.count());
        assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    void testMultipleMetrics() {
        // 记录多种指标
        metricsCollector.recordSuccess();
        metricsCollector.recordSuccess();
        metricsCollector.recordFailure("TestException");
        metricsCollector.recordExpired();
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheMiss();
        metricsCollector.recordValidationTime(TimeUnit.MILLISECONDS.toNanos(15));

        // 验证所有计数器
        assertEquals(2.0, meterRegistry.find("jwt.validation.success").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.validation.failure").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.validation.expired").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.cache.hit").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.cache.miss").counter().count(), 0.001);
        assertEquals(1, meterRegistry.find("jwt.validation.time").timer().count());
    }

    @Test
    void testConcurrentMetrics() throws InterruptedException {
        // 并发记录指标
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    metricsCollector.recordSuccess();
                    metricsCollector.recordCacheHit();
                    metricsCollector.recordValidationTime(TimeUnit.MILLISECONDS.toNanos(5));
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证计数
        assertEquals(1000.0, meterRegistry.find("jwt.validation.success").counter().count(), 0.001);
        assertEquals(1000.0, meterRegistry.find("jwt.cache.hit").counter().count(), 0.001);
        assertEquals(1000, meterRegistry.find("jwt.validation.time").timer().count());
    }

    @Test
    void testDifferentErrorTypes() {
        // 记录不同类型的错误
        metricsCollector.recordFailure("SignatureException");
        metricsCollector.recordFailure("MalformedJwtException");
        metricsCollector.recordFailure("UnsupportedJwtException");
        metricsCollector.recordFailure("IllegalArgumentException");

        // 验证每种错误类型都有独立的计数器
        assertEquals(1.0, meterRegistry.find("jwt.validation.failure")
                .tag("error", "SignatureException").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.validation.failure")
                .tag("error", "MalformedJwtException").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.validation.failure")
                .tag("error", "UnsupportedJwtException").counter().count(), 0.001);
        assertEquals(1.0, meterRegistry.find("jwt.validation.failure")
                .tag("error", "IllegalArgumentException").counter().count(), 0.001);
    }

    @Test
    void testValidationTimeAccuracy() {
        // 测试验证时间的精度
        long exactTime = TimeUnit.MILLISECONDS.toNanos(123); // 123ms

        metricsCollector.recordValidationTime(exactTime);

        Timer timer = meterRegistry.find("jwt.validation.time").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        
        // 验证记录的时间接近预期值
        double totalTimeMs = timer.totalTime(TimeUnit.MILLISECONDS);
        assertTrue(totalTimeMs >= 120 && totalTimeMs <= 125, 
                "Expected time around 123ms, but got " + totalTimeMs + "ms");
    }

    @Test
    void testZeroValidationTime() {
        // 测试零验证时间
        metricsCollector.recordValidationTime(0);

        Timer timer = meterRegistry.find("jwt.validation.time").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(0.0, timer.totalTime(TimeUnit.NANOSECONDS), 0.001);
    }
}
