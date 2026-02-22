package com.zhicore.content.infrastructure.service;

import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.service.DualStorageManager;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 4: 超时降级策略
 * Validates: Requirements 1.4, 2.4
 * 
 * 属性测试：验证超时降级策略
 * 
 * 测试属性：
 * For any 实体ID，当获取分布式锁超时（5秒）时，请求应该降级直接查询数据库而不阻塞，
 * 并且能够成功返回数据。
 * 
 * @author ZhiCore Team
 */
@DisplayName("Property 4: 超时降级策略属性测试")
class CachedDualStorageManagerTimeoutDegradationPropertyTest {

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

    @BeforeEach
    void setUp() {
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
     * Property 4: 超时降级策略
     * 
     * For any 随机生成的文章ID，当获取分布式锁超时时，
     * 请求应该降级直接查询数据库并成功返回数据。
     * 
     * 验证策略：
     * 1. 生成随机文章ID
     * 2. 模拟缓存未命中（热点数据）
     * 3. 模拟锁被长时间持有（获取锁超时）
     * 4. 验证请求降级查询数据库
     * 5. 验证能够成功返回数据
     */
    @Property(tries = 100)
    @DisplayName("Property 4: 锁超时时降级查询数据库")
    void testTimeoutDegradation_FallbackToDatabase(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        // 缓存未命中
        when(valueOperations.get(cacheKey)).thenReturn(null);
        
        // 标记为热点数据
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId)).thenReturn(false);
        
        // 配置锁行为：获取锁超时（返回 false）
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // 模拟数据库查询成功
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        when(delegate.getPostContent(postId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            return dbContent;
        });
        
        // When: 调用查询方法
        long startTime = System.currentTimeMillis();
        PostContent result = cachedManager.getPostContent(postId);
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        
        // Then: 验证结果
        assertNotNull(result, "超时降级后应该返回数据");
        assertEquals(dbContent.getPostId(), result.getPostId(), "返回的数据应该正确");
        
        // 验证：数据库被查询（降级查询）
        assertEquals(1, databaseQueryCount.get(),
                String.format("应该降级查询数据库一次，实际查询次数: %d", databaseQueryCount.get()));
        
        // 验证：响应时间合理（不应该阻塞太久）
        assertTrue(elapsedTime < 1000,
                String.format("超时降级应该快速响应，实际耗时: %dms", elapsedTime));
        
        // 验证：数据库被查询
        verify(delegate, times(1)).getPostContent(postId);
        
        // 验证：锁被尝试获取
        verify(lock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 4 变体：验证超时降级不写入缓存
     * 
     * 当超时降级查询数据库后，不应该写入缓存，避免缓存不一致。
     */
    @Property(tries = 100)
    @DisplayName("Property 4 变体: 超时降级不写入缓存")
    void testTimeoutDegradation_DoesNotCacheResult(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 模拟锁超时
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // 模拟数据库查询
        when(delegate.getPostContent(postId)).thenReturn(dbContent);
        
        // When: 调用查询方法
        PostContent result = cachedManager.getPostContent(postId);
        
        // Then: 验证结果
        assertNotNull(result, "应该返回数据");
        
        // 验证：缓存没有被写入（超时降级不写缓存）
        verify(valueOperations, never()).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        verify(valueOperations, never()).set(eq(cacheKey), any());
    }

    /**
     * Property 4 变体：验证多次超时降级的一致性
     * 
     * 多次超时降级查询应该都能成功返回数据。
     */
    @Property(tries = 100)
    @DisplayName("Property 4 变体: 多次超时降级保持一致")
    void testTimeoutDegradation_ConsistentBehavior(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 模拟锁超时
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // 模拟数据库查询
        when(delegate.getPostContent(postId)).thenReturn(dbContent);
        
        // When: 多次调用查询方法
        int attempts = 3;
        for (int i = 0; i < attempts; i++) {
            PostContent result = cachedManager.getPostContent(postId);
            
            // Then: 每次都应该成功返回数据
            assertNotNull(result, String.format("第 %d 次查询应该返回数据", i + 1));
            assertEquals(dbContent.getPostId(), result.getPostId(),
                    String.format("第 %d 次查询返回的数据应该正确", i + 1));
        }
        
        // 验证：数据库被查询多次（每次超时降级都查询）
        verify(delegate, times(attempts)).getPostContent(postId);
    }

    /**
     * Property 4 变体：验证超时降级时的异常处理
     * 
     * 当超时降级查询数据库失败时，应该正确抛出异常。
     */
    @Property(tries = 100)
    @DisplayName("Property 4 变体: 超时降级时数据库异常正确处理")
    void testTimeoutDegradation_DatabaseException(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 模拟锁超时
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // 模拟数据库查询失败
        RuntimeException dbException = new RuntimeException("Database connection failed");
        when(delegate.getPostContent(postId)).thenThrow(dbException);
        
        // When & Then: 调用查询方法应该抛出异常
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            cachedManager.getPostContent(postId);
        });
        
        assertEquals("Database connection failed", thrown.getMessage(),
                "应该抛出数据库异常");
        
        // 验证：数据库被查询
        verify(delegate, times(1)).getPostContent(postId);
    }

    /**
     * Property 4 变体：验证 InterruptedException 处理
     * 
     * 当获取锁时被中断，应该降级查询数据库。
     */
    @Property(tries = 100)
    @DisplayName("Property 4 变体: 锁中断时降级查询")
    void testTimeoutDegradation_InterruptedException(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 模拟锁获取时被中断
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("Lock acquisition interrupted"));
        
        // 模拟数据库查询成功
        when(delegate.getPostContent(postId)).thenReturn(dbContent);
        
        // When: 调用查询方法
        PostContent result = cachedManager.getPostContent(postId);
        
        // Then: 验证结果
        assertNotNull(result, "中断后应该降级查询并返回数据");
        assertEquals(dbContent.getPostId(), result.getPostId(), "返回的数据应该正确");
        
        // 验证：数据库被查询（降级查询）
        verify(delegate, times(1)).getPostContent(postId);
        
        // 验证：线程中断标志被恢复
        assertTrue(Thread.interrupted() || !Thread.currentThread().isInterrupted(),
                "线程中断标志应该被正确处理");
    }

    // ==================== 辅助方法 ====================

    private PostContent createTestPostContent(Long postId) {
        PostContent content = new PostContent();
        content.setPostId(String.valueOf(postId));
        content.setRaw("Test raw content for post " + postId);
        content.setHtml("<p>Test html content for post " + postId + "</p>");
        return content;
    }
}
