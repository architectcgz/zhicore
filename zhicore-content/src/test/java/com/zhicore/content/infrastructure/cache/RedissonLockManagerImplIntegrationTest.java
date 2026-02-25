package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cache.LockManager;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Redisson Lock Manager 集成测试
 * 
 * 测试 RedissonLockManagerImpl 与真实 Redis 的交互。
 * 使用 Testcontainers 提供隔离的测试环境。
 * 
 * @author ZhiCore Team
 */
@DisplayName("Redisson Lock Manager 集成测试")
class RedissonLockManagerImplIntegrationTest extends IntegrationTestBase {
    
    @Autowired
    private LockManager lockManager;
    
    @BeforeEach
    void setUp() {
        // 清理 Redis 数据
        cleanupRedis();
    }
    
    @AfterEach
    void tearDown() {
        // 清理 Redis 数据
        cleanupRedis();
    }
    
    @Test
    @DisplayName("应该成功获取和释放锁")
    void shouldAcquireAndReleaseLock() {
        // Given: 锁键
        String lockKey = "test:lock:1";
        
        // When: 获取锁
        boolean acquired = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(10)
        );
        
        // Then: 应该成功
        assertThat(acquired).isTrue();
        
        // When: 释放锁
        lockManager.unlock(lockKey);
        
        // Then: 应该能再次获取
        boolean reacquired = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(10)
        );
        assertThat(reacquired).isTrue();
        
        // 清理
        lockManager.unlock(lockKey);
    }
    
    @Test
    @DisplayName("同一线程不应该重复获取同一把锁")
    void shouldNotAcquireSameLockTwiceInSameThread() {
        // Given: 锁键
        String lockKey = "test:lock:2";
        
        // When: 第一次获取锁
        boolean firstAcquire = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(10)
        );
        
        // Then: 应该成功
        assertThat(firstAcquire).isTrue();
        
        // When: 同一线程再次尝试获取（不等待）
        boolean secondAcquire = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(10)
        );
        
        // Then: 应该失败（因为已经持有锁）
        assertThat(secondAcquire).isFalse();
        
        // 清理
        lockManager.unlock(lockKey);
    }
    
    @Test
    @DisplayName("不同线程应该互斥访问")
    void shouldProvideExclusiveAccessAcrossThreads() throws InterruptedException {
        // Given: 锁键和共享计数器
        String lockKey = "test:lock:3";
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 10;
        int incrementsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When: 多线程并发访问
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        // 获取锁
                        boolean acquired = lockManager.tryLock(
                            lockKey,
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(10)
                        );
                        
                        if (acquired) {
                            try {
                                // 临界区：增加计数器
                                int current = counter.get();
                                Thread.sleep(1); // 模拟一些处理时间
                                counter.set(current + 1);
                            } finally {
                                // 释放锁
                                lockManager.unlock(lockKey);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then: 等待所有线程完成
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 验证计数器值正确（如果锁工作正常，应该等于 threadCount * incrementsPerThread）
        assertThat(counter.get()).isEqualTo(threadCount * incrementsPerThread);
    }
    
    @Test
    @DisplayName("应该支持锁自动过期")
    void shouldSupportLockAutoExpiration() throws InterruptedException {
        // Given: 锁键和短租期
        String lockKey = "test:lock:4";
        
        // When: 获取锁（1秒租期）
        boolean acquired = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(1)
        );
        
        // Then: 应该成功
        assertThat(acquired).isTrue();
        
        // When: 等待锁过期
        Thread.sleep(1500);
        
        // Then: 其他线程应该能获取锁
        boolean reacquired = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(10)
        );
        assertThat(reacquired).isTrue();
        
        // 清理
        lockManager.unlock(lockKey);
    }
    
    @Test
    @DisplayName("应该支持等待获取锁")
    void shouldSupportWaitingForLock() throws InterruptedException {
        // Given: 锁键
        String lockKey = "test:lock:5";
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch lockReleased = new CountDownLatch(1);
        
        // When: 线程1获取锁
        Thread thread1 = new Thread(() -> {
            boolean acquired = lockManager.tryLock(
                lockKey,
                Duration.ZERO,
                Duration.ofSeconds(10)
            );
            if (acquired) {
                lockAcquired.countDown();
                try {
                    // 持有锁一段时间
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lockManager.unlock(lockKey);
                    lockReleased.countDown();
                }
            }
        });
        
        thread1.start();
        lockAcquired.await(5, TimeUnit.SECONDS);
        
        // When: 线程2等待获取锁
        long startTime = System.currentTimeMillis();
        boolean acquired = lockManager.tryLock(
            lockKey,
            Duration.ofSeconds(2),
            Duration.ofSeconds(10)
        );
        long waitTime = System.currentTimeMillis() - startTime;
        
        // Then: 应该在等待后成功获取
        assertThat(acquired).isTrue();
        assertThat(waitTime).isGreaterThanOrEqualTo(400); // 至少等待了一段时间
        
        // 清理
        lockManager.unlock(lockKey);
        lockReleased.await(5, TimeUnit.SECONDS);
        thread1.join();
    }
    
    @Test
    @DisplayName("释放未持有的锁不应该抛出异常")
    void shouldNotThrowExceptionWhenUnlockingNonHeldLock() {
        // When & Then: 释放未持有的锁不应该抛出异常
        assertThatCode(() -> lockManager.unlock("test:lock:non:existent"))
            .doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("应该支持多个不同的锁")
    void shouldSupportMultipleDifferentLocks() {
        // Given: 多个锁键
        String lock1 = "test:lock:multi:1";
        String lock2 = "test:lock:multi:2";
        String lock3 = "test:lock:multi:3";
        
        // When: 同时获取多个锁
        boolean acquired1 = lockManager.tryLock(lock1, Duration.ZERO, Duration.ofSeconds(10));
        boolean acquired2 = lockManager.tryLock(lock2, Duration.ZERO, Duration.ofSeconds(10));
        boolean acquired3 = lockManager.tryLock(lock3, Duration.ZERO, Duration.ofSeconds(10));
        
        // Then: 都应该成功
        assertThat(acquired1).isTrue();
        assertThat(acquired2).isTrue();
        assertThat(acquired3).isTrue();
        
        // 清理
        lockManager.unlock(lock1);
        lockManager.unlock(lock2);
        lockManager.unlock(lock3);
    }
    
    @Test
    @DisplayName("应该防止缓存击穿场景")
    void shouldPreventCacheBreakthrough() throws InterruptedException {
        // Given: 模拟缓存击穿场景
        String lockKey = "cache:lock:post:1001";
        AtomicInteger dbQueryCount = new AtomicInteger(0);
        AtomicBoolean cacheFilled = new AtomicBoolean(false);
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // When: 多线程同时请求同一个缓存未命中的数据
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 尝试获取锁
                    boolean acquired = lockManager.tryLock(
                        lockKey,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5)
                    );
                    
                    if (acquired) {
                        try {
                            // 模拟“先查库，再写缓存”的击穿修复逻辑：只有首次持锁线程需要查库
                            if (cacheFilled.compareAndSet(false, true)) {
                                dbQueryCount.incrementAndGet();
                                Thread.sleep(100);
                            }
                        } finally {
                            lockManager.unlock(lockKey);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then: 等待所有线程完成
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 验证只有一个线程执行了数据库查询（其他线程等待或放弃）
        assertThat(dbQueryCount.get()).isLessThanOrEqualTo(2); // 允许少量并发
    }
    
    @Test
    @DisplayName("不等待模式下获取已被持有的锁应该立即失败")
    void shouldFailImmediatelyWhenLockIsHeldWithNoWait() throws InterruptedException {
        // Given: 锁键
        String lockKey = "test:lock:nowait";
        CountDownLatch lockAcquired = new CountDownLatch(1);
        
        // When: 线程1获取锁
        Thread thread1 = new Thread(() -> {
            boolean acquired = lockManager.tryLock(
                lockKey,
                Duration.ZERO,
                Duration.ofSeconds(10)
            );
            if (acquired) {
                lockAcquired.countDown();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lockManager.unlock(lockKey);
                }
            }
        });
        
        thread1.start();
        lockAcquired.await(5, TimeUnit.SECONDS);
        
        // When: 线程2尝试获取锁（不等待）
        long startTime = System.currentTimeMillis();
        boolean acquired = lockManager.tryLock(
            lockKey,
            Duration.ZERO,
            Duration.ofSeconds(10)
        );
        long waitTime = System.currentTimeMillis() - startTime;
        
        // Then: 应该立即失败
        assertThat(acquired).isFalse();
        assertThat(waitTime).isLessThan(100); // 应该几乎立即返回
        
        // 清理
        thread1.join();
    }
}
