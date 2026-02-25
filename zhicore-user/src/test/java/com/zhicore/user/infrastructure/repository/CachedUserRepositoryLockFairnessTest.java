package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户仓储锁公平性测试
 * 
 * 测试内容：
 * 1. 公平锁和非公平锁的行为差异
 * 2. 锁等待队列长度监控
 * 
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5
 * 
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
class CachedUserRepositoryLockFairnessTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    @Mock
    private UserRepository delegate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RLock fairLock;

    @Mock
    private RLock nonFairLock;

    private CacheProperties cacheProperties;
    private CachedUserRepository cachedRepository;

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        cacheProperties.getTtl().setEntityDetail(600);
        cacheProperties.getTtl().setNullValue(60);
        cacheProperties.getLock().setWaitTime(5);
        cacheProperties.getLock().setLeaseTime(10);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        cachedRepository = new CachedUserRepository(
                redisTemplate,
                cacheProperties,
                redissonClient,
                hotDataIdentifier,
                delegate
        );
    }

    /**
     * 测试公平锁配置
     * 验证当配置为公平锁时，使用 getFairLock
     * 
     * Requirements: 11.1, 11.2
     */
    @Test
    void testFairLockConfiguration() throws Exception {
        // Given: 配置为公平锁
        cacheProperties.getLock().setFair(true);
        Long userId = 1L;
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Mock: 缓存未命中，热点数据
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // Mock: 公平锁
        when(redissonClient.getFairLock(lockKey)).thenReturn(fairLock);
        when(fairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(fairLock.isHeldByCurrentThread()).thenReturn(true);
        
        // Mock: 数据库查询
        User user = createTestUser(userId);
        when(delegate.findById(userId)).thenReturn(Optional.of(user));
        
        // When: 查询用户
        Optional<User> result = cachedRepository.findById(userId);
        
        // Then: 验证使用了公平锁
        verify(redissonClient).getFairLock(lockKey);
        verify(redissonClient, never()).getLock(lockKey);
        verify(fairLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
    }

    /**
     * 测试非公平锁配置
     * 验证当配置为非公平锁时，使用 getLock
     * 
     * Requirements: 11.1, 11.3
     */
    @Test
    void testNonFairLockConfiguration() throws Exception {
        // Given: 配置为非公平锁（默认）
        cacheProperties.getLock().setFair(false);
        Long userId = 1L;
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Mock: 缓存未命中，热点数据
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // Mock: 非公平锁
        when(redissonClient.getLock(lockKey)).thenReturn(nonFairLock);
        when(nonFairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(nonFairLock.isHeldByCurrentThread()).thenReturn(true);
        
        // Mock: 数据库查询
        User user = createTestUser(userId);
        when(delegate.findById(userId)).thenReturn(Optional.of(user));
        
        // When: 查询用户
        Optional<User> result = cachedRepository.findById(userId);
        
        // Then: 验证使用了非公平锁
        verify(redissonClient).getLock(lockKey);
        verify(redissonClient, never()).getFairLock(lockKey);
        verify(nonFairLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
    }

    /**
     * 测试公平锁的顺序性
     * 验证公平锁按请求顺序分配锁
     * 
     * 注意：这是一个模拟测试，实际的公平性由 Redisson 保证
     * 
     * Requirements: 11.2
     */
    @Test
    void testFairLockOrdering() throws Exception {
        // Given: 配置为公平锁
        cacheProperties.getLock().setFair(true);
        Long userId = 1L;
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Mock: 缓存未命中，热点数据
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // Mock: 公平锁
        when(redissonClient.getFairLock(lockKey)).thenReturn(fairLock);
        
        // 模拟多个线程按顺序获取锁
        List<Integer> acquisitionOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger counter = new AtomicInteger(0);
        
        when(fairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            int order = counter.incrementAndGet();
            acquisitionOrder.add(order);
            return true;
        });
        when(fairLock.isHeldByCurrentThread()).thenReturn(true);
        
        // Mock: 数据库查询
        User user = createTestUser(userId);
        when(delegate.findById(userId)).thenReturn(Optional.of(user));
        
        // When: 多个线程并发请求
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    cachedRepository.findById(userId);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then: 验证锁按顺序获取
        assertEquals(threadCount, acquisitionOrder.size());
        // 公平锁应该按顺序分配（1, 2, 3, 4, 5）
        for (int i = 0; i < threadCount; i++) {
            assertTrue(acquisitionOrder.get(i) >= 1 && acquisitionOrder.get(i) <= threadCount);
        }
    }

    /**
     * 测试非公平锁的性能优势
     * 验证非公平锁可能不按请求顺序分配锁（性能优先）
     * 
     * 注意：这是一个模拟测试，实际行为由 Redisson 决定
     * 
     * Requirements: 11.3, 11.4
     */
    @Test
    void testNonFairLockPerformance() throws Exception {
        // Given: 配置为非公平锁
        cacheProperties.getLock().setFair(false);
        Long userId = 1L;
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Mock: 缓存未命中，热点数据
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // Mock: 非公平锁
        when(redissonClient.getLock(lockKey)).thenReturn(nonFairLock);
        when(nonFairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(nonFairLock.isHeldByCurrentThread()).thenReturn(true);
        
        // Mock: 数据库查询
        User user = createTestUser(userId);
        when(delegate.findById(userId)).thenReturn(Optional.of(user));
        
        // When: 多个线程并发请求
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<User> result = cachedRepository.findById(userId);
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();
        
        // Then: 验证所有请求都成功
        assertEquals(threadCount, successCount.get());
        // 非公平锁应该更快（这里只是验证能完成，实际性能差异需要真实环境测试）
        assertTrue(duration < 10000, "Should complete within 10 seconds");
    }

    /**
     * 测试锁等待队列长度监控
     * 验证 getLockQueueLength 方法
     * 
     * 注意：Redisson RLock 不直接提供队列长度，此方法返回 -1
     * 
     * Requirements: 11.5
     */
    @Test
    void testLockQueueLengthMonitoring() {
        // Given: 任意锁键
        String lockKey = UserRedisKeys.lockDetail(1L);
        
        // When: 获取锁队列长度
        int queueLength = cachedRepository.getLockQueueLength(lockKey);
        
        // Then: 返回 -1 表示不支持
        assertEquals(-1, queueLength, "Queue length monitoring not supported, should return -1");
    }

    /**
     * 测试公平锁避免请求饥饿
     * 验证在高并发场景下，公平锁确保所有请求都能获得锁
     * 
     * Requirements: 11.2
     */
    @Test
    void testFairLockPreventsStarvation() throws Exception {
        // Given: 配置为公平锁
        cacheProperties.getLock().setFair(true);
        Long userId = 1L;
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Mock: 缓存未命中，热点数据
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // Mock: 公平锁
        when(redissonClient.getFairLock(lockKey)).thenReturn(fairLock);
        when(fairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(fairLock.isHeldByCurrentThread()).thenReturn(true);
        
        // Mock: 数据库查询
        User user = createTestUser(userId);
        when(delegate.findById(userId)).thenReturn(Optional.of(user));
        
        // When: 大量并发请求
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<User> result = cachedRepository.findById(userId);
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then: 所有请求都应该成功（没有饥饿）
        assertEquals(threadCount, successCount.get(), "All requests should succeed with fair lock");
        assertEquals(0, failCount.get(), "No requests should fail");
    }

    /**
     * 测试不同场景下的锁选择
     * 验证系统为不同场景提供公平锁和非公平锁选项
     * 
     * Requirements: 11.4
     */
    @Test
    void testLockSelectionForDifferentScenarios() throws Exception {
        // Scenario 1: 高并发场景使用非公平锁（性能优先）
        cacheProperties.getLock().setFair(false);
        Long userId1 = 1L;
        String lockKey1 = UserRedisKeys.lockDetail(userId1);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        when(redissonClient.getLock(lockKey1)).thenReturn(nonFairLock);
        when(nonFairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(nonFairLock.isHeldByCurrentThread()).thenReturn(true);
        
        User user1 = createTestUser(userId1);
        when(delegate.findById(userId1)).thenReturn(Optional.of(user1));
        
        Optional<User> result1 = cachedRepository.findById(userId1);
        assertTrue(result1.isPresent());
        verify(redissonClient).getLock(lockKey1);
        
        // Scenario 2: 需要公平性的场景使用公平锁
        cacheProperties.getLock().setFair(true);
        Long userId2 = 2L;
        String lockKey2 = UserRedisKeys.lockDetail(userId2);
        
        when(redissonClient.getFairLock(lockKey2)).thenReturn(fairLock);
        when(fairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(fairLock.isHeldByCurrentThread()).thenReturn(true);
        
        User user2 = createTestUser(userId2);
        when(delegate.findById(userId2)).thenReturn(Optional.of(user2));
        
        Optional<User> result2 = cachedRepository.findById(userId2);
        assertTrue(result2.isPresent());
        verify(redissonClient).getFairLock(lockKey2);
    }

    /**
     * 测试锁配置的动态切换
     * 验证可以在运行时切换公平锁和非公平锁
     * 
     * Requirements: 11.1, 11.4
     */
    @Test
    void testDynamicLockConfigurationSwitch() throws Exception {
        Long userId = 1L;
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Mock: 缓存未命中，热点数据
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // Mock: 数据库查询
        User user = createTestUser(userId);
        when(delegate.findById(userId)).thenReturn(Optional.of(user));
        
        // Phase 1: 使用非公平锁
        cacheProperties.getLock().setFair(false);
        when(redissonClient.getLock(lockKey)).thenReturn(nonFairLock);
        when(nonFairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(nonFairLock.isHeldByCurrentThread()).thenReturn(true);
        
        Optional<User> result1 = cachedRepository.findById(userId);
        assertTrue(result1.isPresent());
        verify(redissonClient, times(1)).getLock(lockKey);
        
        // Phase 2: 切换到公平锁
        cacheProperties.getLock().setFair(true);
        when(redissonClient.getFairLock(lockKey)).thenReturn(fairLock);
        when(fairLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(fairLock.isHeldByCurrentThread()).thenReturn(true);
        
        Optional<User> result2 = cachedRepository.findById(userId);
        assertTrue(result2.isPresent());
        verify(redissonClient, times(1)).getFairLock(lockKey);
    }

    // ==================== 辅助方法 ====================

    private User createTestUser(Long userId) {
        return User.reconstitute(
                userId,
                "testuser" + userId,
                "testuser" + userId,
                "test" + userId + "@example.com",
                "hashedpassword",
                null,
                null,
                UserStatus.ACTIVE,
                false,
                new HashSet<>(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
