package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 5: 异常时锁释放
 * Validates: Requirements 2.6
 * 
 * 属性测试：验证异常时锁的正确释放
 * 
 * 测试属性：
 * For any 用户ID，当数据库查询失败或发生异常时，分布式锁应该被正确释放，不应该导致死锁。
 * 
 * @author ZhiCore Team
 */
class CachedUserRepositoryExceptionLockReleasePropertyTest {

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
    private UserRepository delegate;

    private CachedUserRepository cachedRepository;

    private static final String ENTITY_TYPE_USER = "user";

    /**
     * Initialize mocks and create the cached repository instance.
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
        cachedRepository = new CachedUserRepository(
                redisTemplate,
                cacheProperties,
                redissonClient,
                hotDataIdentifier,
                delegate
        );
    }

    /**
     * Property 5: 异常时锁释放
     * 
     * For any 随机生成的用户ID，当数据库查询失败时，分布式锁应该被正确释放。
     */
    @Property(tries = 100)
    void testExceptionLockRelease_DatabaseQueryFailed(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，数据库查询失败
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_USER, userId)).thenReturn(false);
        
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 模拟数据库查询失败
        RuntimeException dbException = new RuntimeException("Database query failed for user " + userId);
        when(delegate.findById(userId)).thenThrow(dbException);
        
        // When: 调用方法（实现会捕获异常并降级）
        try {
            cachedRepository.findById(userId);
        } catch (Exception e) {
            // 可能抛出异常，也可能降级处理
        }
        
        // Then: 核心验证：锁被正确释放
        verify(lock, times(1)).unlock();
        
        // 验证：锁被获取
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        
        // 验证：数据库被查询（可能多次，因为有降级逻辑）
        verify(delegate, atLeastOnce()).findById(userId);
        
        // 验证：没有写入缓存（因为查询失败）
        verify(valueOperations, never()).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 5 变体：缓存写入失败时锁被正确释放
     */
    @Property(tries = 100)
    void testExceptionLockRelease_CacheWriteFailed(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，缓存写入失败
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库查询成功
        when(delegate.findById(userId)).thenReturn(Optional.empty());
        
        // 缓存写入失败
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        
        // When: 调用方法
        try {
            cachedRepository.findById(userId);
        } catch (Exception e) {
            // 缓存写入失败可能被捕获
        }
        
        // Then: 核心验证：锁被正确释放
        verify(lock, times(1)).unlock();
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).findById(userId);
    }

    /**
     * Property 5 变体：锁释放失败不影响业务流程
     */
    @Property(tries = 100)
    void testExceptionLockRelease_UnlockFailed_NoImpact(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，锁释放失败
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        when(delegate.findById(userId)).thenReturn(Optional.empty());
        
        // 锁释放失败
        doThrow(new RuntimeException("Lock release failed")).when(lock).unlock();
        
        // When: 调用方法
        try {
            cachedRepository.findById(userId);
        } catch (Exception e) {
            // 锁释放失败可能被捕获
        }
        
        // Then: 验证业务逻辑正常执行
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).findById(userId);
        verify(lock, times(1)).unlock();
    }

    /**
     * Property 5 变体：多种异常场景下锁都被释放
     */
    @Property(tries = 100)
    void testExceptionLockRelease_VariousExceptions(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId,
            @ForAll("exceptionProvider") Exception exception) throws Exception {
        
        // Setup for this property
        setUp();
        
        // Given: 配置缓存未命中，热点数据，数据库查询抛出各种异常
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        when(delegate.findById(userId)).thenThrow(exception);
        
        // When: 调用方法（实现会捕获异常并降级）
        try {
            cachedRepository.findById(userId);
        } catch (Exception e) {
            // 可能抛出异常，也可能降级处理
        }
        
        // Then: 核心验证：无论什么异常，锁都被正确释放
        verify(lock, times(1)).unlock();
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, atLeastOnce()).findById(userId);
    }

    // ==================== 辅助方法 ====================

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
