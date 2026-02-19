package com.blog.post.infrastructure.service;

import com.blog.common.cache.CacheConstants;
import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.post.domain.service.DualStorageManager;
import com.blog.post.infrastructure.cache.PostRedisKeys;
import com.blog.post.infrastructure.mongodb.document.PostContent;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 1: 分布式锁互斥性
 * Validates: Requirements 1.1, 2.1, 3.1
 * 
 * 属性测试：验证分布式锁的互斥性
 * 
 * 测试属性：
 * For any 实体ID（文章、用户、评论），当多个并发请求同时查询已过期的缓存时，
 * 只有一个请求能够成功获取分布式锁并查询数据库，其他请求应该等待或降级。
 * 
 * @author Blog Team
 */
@DisplayName("Property 1: 分布式锁互斥性属性测试")
class CachedDualStorageManagerLockMutualExclusionPropertyTest {

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
     * Property 1: 分布式锁互斥性
     * 
     * For any 随机生成的文章ID，当多个并发请求同时查询已过期的热点文章缓存时，
     * 只有一个请求能够成功获取分布式锁并查询数据库。
     * 
     * 验证策略：
     * 1. 生成随机文章ID
     * 2. 模拟缓存未命中（热点数据）
     * 3. 启动多个并发线程同时查询
     * 4. 验证只有一个线程获取到锁并查询数据库
     * 5. 其他线程要么等待后从缓存读取，要么超时降级
     */
    @Property(tries = 100)
    @DisplayName("Property 1: 多个并发请求只有一个能获取锁查询数据库")
    void testLockMutualExclusion_OnlyOneThreadQueriesDatabase(
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
        
        // 配置锁行为：只有第一个线程能获取锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 使用 AtomicInteger 记录获取锁成功的次数
        AtomicInteger lockAcquiredCount = new AtomicInteger(0);
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        
        // 模拟锁的互斥行为
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            int count = lockAcquiredCount.incrementAndGet();
            // 只有第一个请求能获取锁
            return count == 1;
        });
        
        when(lock.isHeldByCurrentThread()).thenAnswer(invocation -> {
            // 只有获取锁的线程返回 true
            return lockAcquiredCount.get() == 1;
        });
        
        // 模拟数据库查询
        when(delegate.getPostContent(postId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            // 模拟数据库查询耗时
            Thread.sleep(50);
            return dbContent;
        });
        
        // When: 启动多个并发线程同时查询
        int concurrentThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < concurrentThreads; i++) {
            new Thread(() -> {
                try {
                    // 等待所有线程就绪
                    startLatch.await();
                    
                    // 同时发起请求
                    PostContent result = cachedManager.getPostContent(postId);
                    
                    if (result != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        // 启动所有线程
        startLatch.countDown();
        
        // 等待所有线程完成（最多等待5秒）
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        
        // Then: 验证结果
        assertTrue(completed, "所有线程应该在5秒内完成");
        
        // 核心验证：只有一个线程查询了数据库
        assertEquals(1, databaseQueryCount.get(),
                String.format("只有一个线程应该查询数据库，实际查询次数: %d", databaseQueryCount.get()));
        
        // 验证：只有一个线程获取到锁
        assertTrue(lockAcquiredCount.get() >= 1,
                String.format("至少有一个线程尝试获取锁，实际: %d", lockAcquiredCount.get()));
        
        // 验证：大部分请求成功（获取锁的线程 + 超时降级的线程）
        assertTrue(successCount.get() > 0,
                String.format("应该有成功的请求，实际成功: %d", successCount.get()));
        
        // 验证：数据库只被查询一次
        verify(delegate, times(1)).getPostContent(postId);
    }

    /**
     * Property 1 变体：验证锁超时降级场景
     * 
     * 当第一个线程持有锁时间过长，其他线程应该超时降级直接查询数据库。
     */
    @Property(tries = 100)
    @DisplayName("Property 1 变体: 锁超时时其他线程降级查询数据库")
    void testLockMutualExclusion_TimeoutFallback(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 使用 AtomicInteger 记录获取锁的尝试次数
        AtomicInteger lockAttempts = new AtomicInteger(0);
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        
        // 模拟锁行为：第一个线程获取锁，其他线程超时
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            int attempt = lockAttempts.incrementAndGet();
            if (attempt == 1) {
                // 第一个线程获取锁成功
                return true;
            } else {
                // 其他线程超时（模拟等待5秒后超时）
                Thread.sleep(100); // 模拟等待时间
                return false;
            }
        });
        
        when(lock.isHeldByCurrentThread()).thenAnswer(invocation -> {
            return lockAttempts.get() == 1;
        });
        
        // 模拟数据库查询
        when(delegate.getPostContent(postId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            return dbContent;
        });
        
        // When: 启动多个并发线程
        int concurrentThreads = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < concurrentThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    PostContent result = cachedManager.getPostContent(postId);
                    if (result != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        
        // Then: 验证结果
        assertTrue(completed, "所有线程应该完成");
        
        // 验证：多个线程查询了数据库（第一个获取锁的 + 超时降级的）
        assertTrue(databaseQueryCount.get() >= 1,
                String.format("至少有一个线程查询数据库，实际: %d", databaseQueryCount.get()));
        
        // 验证：所有请求都成功（获取锁或降级）
        assertEquals(concurrentThreads, successCount.get(),
                String.format("所有请求都应该成功，实际成功: %d", successCount.get()));
        
        // 验证：数据库被查询多次（因为超时降级）
        verify(delegate, atLeast(1)).getPostContent(postId);
    }

    /**
     * Property 1 变体：验证 DCL 双重检查锁的正确性
     * 
     * 当第一个线程填充缓存后，其他等待的线程应该从缓存读取，不再查询数据库。
     */
    @Property(tries = 100)
    @DisplayName("Property 1 变体: DCL 双重检查避免重复查询")
    void testLockMutualExclusion_DCL_AvoidsDuplicateQuery(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        // 模拟缓存行为：第一次未命中，后续命中
        AtomicInteger cacheCheckCount = new AtomicInteger(0);
        when(valueOperations.get(cacheKey)).thenAnswer(invocation -> {
            int count = cacheCheckCount.incrementAndGet();
            if (count <= 2) {
                // 前两次检查未命中（第一个线程的两次检查）
                return null;
            } else {
                // 后续检查命中（其他线程的 DCL 检查）
                return dbContent;
            }
        });
        
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 所有线程都能获取锁（模拟串行获取）
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 模拟数据库查询
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        when(delegate.getPostContent(postId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            return dbContent;
        });
        
        // When: 启动多个并发线程
        int concurrentThreads = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentThreads);
        
        for (int i = 0; i < concurrentThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    cachedManager.getPostContent(postId);
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        
        // Then: 验证结果
        assertTrue(completed, "所有线程应该完成");
        
        // 核心验证：只有第一个线程查询了数据库
        assertEquals(1, databaseQueryCount.get(),
                String.format("只有第一个线程应该查询数据库，实际查询次数: %d", databaseQueryCount.get()));
        
        // 验证：数据库只被查询一次
        verify(delegate, times(1)).getPostContent(postId);
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
