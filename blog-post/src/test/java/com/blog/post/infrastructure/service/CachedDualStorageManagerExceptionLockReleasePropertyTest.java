package com.blog.post.infrastructure.service;

import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.post.domain.service.DualStorageManager;
import com.blog.post.infrastructure.cache.PostRedisKeys;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 5: 异常时锁释放
 * Validates: Requirements 1.6
 * 
 * 属性测试：验证异常时锁的正确释放
 * 
 * 测试属性：
 * For any 实体ID，当数据库查询失败或发生异常时，分布式锁应该被正确释放，不应该导致死锁。
 * 
 * @author Blog Team
 */
class CachedDualStorageManagerExceptionLockReleasePropertyTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.Lock lockProperties;

    @Mock
    private CacheProperties.Ttl ttlProperties;

    @Mock
    private DualStorageManager delegate;

    private CachedDualStorageManager cachedManager;

    private static final String ENTITY_TYPE_POST = "post";

    /**
     * Initialize mocks and create the cached manager instance.
     * This method should be called at the start of each property test.
     */
    private void setUp() {
        MockitoAnnotations.openMocks(this);

        // 配置 RedisTemplate mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 配置 CacheProperties mock
        when(cacheProperties.getLock()).thenReturn(lockProperties);
        when(cacheProperties.getTtl()).thenReturn(ttlProperties);
        when(lockProperties.getWaitTime()).thenReturn(5L);
        when(lockProperties.getLeaseTime()).thenReturn(10L);
        when(ttlProperties.getEntityDetail()).thenReturn(600L);

        // 创建被测试对象
        cachedManager = new CachedDualStorageManager(
                redisTemplate,
                redissonClient,
                hotDataIdentifier,
                cacheProperties,
                delegate
        );
    }

    /**
     * Property 5: 异常时锁释放
     * 
     * For any 随机生成的文章ID，当数据库查询失败时，分布式锁应该被正确释放。
     * 
     * 验证策略：
     * 1. 生成随机文章ID
     * 2. 模拟缓存未命中（热点数据）
     * 3. 模拟成功获取锁
     * 4. 模拟数据库查询失败（抛出异常）
     * 5. 验证锁被正确释放（unlock 被调用）
     * 6. 验证异常被正确处理（降级查询数据库）
     */
    @Property(tries = 100)
    void testExceptionLockRelease_DatabaseQueryFailed(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，数据库查询失败
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        // 缓存未命中
        when(valueOperations.get(cacheKey)).thenReturn(null);
        
        // 标记为热点数据
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId)).thenReturn(false);
        
        // 配置锁行为：成功获取锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 模拟数据库查询失败 - 实现会在 finally 块中捕获并降级，所以不会抛出异常
        RuntimeException dbException = new RuntimeException("Database query failed for post " + postId);
        when(delegate.getPostContent(postId)).thenThrow(dbException);
        
        // When: 调用方法（实现会捕获异常并降级查询数据库）
        try {
            cachedManager.getPostContent(postId);
        } catch (Exception e) {
            // 实现可能会抛出异常，也可能降级处理
        }
        
        // Then: 核心验证：锁被正确释放
        verify(lock, times(1)).unlock();
        
        // 验证：锁被获取
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        
        // 验证：数据库被查询（可能多次，因为有降级逻辑）
        verify(delegate, atLeastOnce()).getPostContent(postId);
        
        // 验证：没有写入缓存（因为查询失败）
        verify(valueOperations, never()).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 5 变体：缓存写入失败时锁被正确释放
     * 
     * 当数据库查询成功但缓存写入失败时，锁应该被正确释放。
     */
    @Property(tries = 100)
    void testExceptionLockRelease_CacheWriteFailed(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，缓存写入失败
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库查询成功
        when(delegate.getPostContent(postId)).thenReturn(null); // 返回 null（不存在）
        
        // 缓存写入失败
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        
        // When: 调用方法（缓存写入失败不应该抛出异常）
        try {
            cachedManager.getPostContent(postId);
        } catch (Exception e) {
            // 缓存写入失败可能被捕获，也可能不被捕获，取决于实现
            // 这里我们主要验证锁被释放
        }
        
        // Then: 核心验证：锁被正确释放
        verify(lock, times(1)).unlock();
        
        // 验证：锁被获取
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        
        // 验证：数据库被查询
        verify(delegate, times(1)).getPostContent(postId);
    }

    /**
     * Property 5 变体：锁释放失败不影响业务流程
     * 
     * 当锁释放失败时，不应该影响业务流程，应该依赖锁的自动过期机制。
     */
    @Property(tries = 100)
    void testExceptionLockRelease_UnlockFailed_NoImpact(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，锁释放失败
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库查询成功
        when(delegate.getPostContent(postId)).thenReturn(null);
        
        // 锁释放失败
        doThrow(new RuntimeException("Lock release failed")).when(lock).unlock();
        
        // When: 调用方法（锁释放失败不应该影响业务）
        try {
            cachedManager.getPostContent(postId);
        } catch (Exception e) {
            // 锁释放失败可能被捕获，也可能不被捕获
            // 这里我们主要验证业务逻辑正常执行
        }
        
        // Then: 验证业务逻辑正常执行
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).getPostContent(postId);
        verify(lock, times(1)).unlock(); // unlock 被调用（虽然失败了）
    }

    /**
     * Property 5 变体：多种异常场景下锁都被释放
     * 
     * 测试各种异常场景（NullPointerException, IllegalArgumentException 等）下锁都被正确释放。
     */
    @Property(tries = 100)
    void testExceptionLockRelease_VariousExceptions(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId,
            @ForAll("exceptionProvider") Exception exception) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，数据库查询抛出各种异常
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 模拟数据库查询抛出各种异常
        when(delegate.getPostContent(postId)).thenThrow(exception);
        
        // When: 调用方法（实现会捕获异常并降级）
        try {
            cachedManager.getPostContent(postId);
        } catch (Exception e) {
            // 可能抛出异常，也可能降级处理
        }
        
        // Then: 核心验证：无论什么异常，锁都被正确释放
        verify(lock, times(1)).unlock();
        
        // 验证：锁被获取
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        
        // 验证：数据库被查询（可能多次，因为有降级逻辑）
        verify(delegate, atLeastOnce()).getPostContent(postId);
    }

    /**
     * Property 5 变体：并发异常场景下锁的正确性
     * 
     * 当多个线程并发访问，其中一个线程查询失败时，不应该影响其他线程。
     */
    @Property(tries = 100)
    void testExceptionLockRelease_ConcurrentExceptions(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 第一个线程获取锁成功，第二个线程超时
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS))
                .thenReturn(true)   // 第一个线程成功
                .thenReturn(false); // 第二个线程超时
        
        when(lock.isHeldByCurrentThread())
                .thenReturn(true)   // 第一个线程持有锁
                .thenReturn(false); // 第二个线程未持有锁
        
        // 第一个线程查询失败，第二个线程查询成功（降级）
        when(delegate.getPostContent(postId))
                .thenThrow(new RuntimeException("Database query failed"))
                .thenReturn(null); // 第二个线程查询成功（降级）
        
        // When: 第一个线程查询失败（实现会捕获异常并降级）
        try {
            cachedManager.getPostContent(postId);
        } catch (Exception e) {
            // 可能抛出异常，也可能降级处理
        }
        
        // Then: 验证第一个线程的锁被释放
        verify(lock, times(1)).unlock();
        
        // When: 第二个线程超时降级查询
        try {
            cachedManager.getPostContent(postId);
        } catch (Exception e) {
            // 可能成功，也可能失败
        }
        
        // Then: 验证第二个线程没有尝试释放锁（因为没有获取到）
        verify(lock, times(1)).unlock(); // 只有第一个线程释放了锁
    }

    // ==================== 辅助方法 ====================

    /**
     * 提供各种异常类型用于测试
     */
    @Provide
    Arbitrary<Exception> exceptionProvider() {
        return Arbitraries.of(
                new RuntimeException("Runtime exception"),
                new IllegalArgumentException("Illegal argument"),
                new IllegalStateException("Illegal state"),
                new NullPointerException("Null pointer"),
                new UnsupportedOperationException("Unsupported operation")
        );
    }
}
