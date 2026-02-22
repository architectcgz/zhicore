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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 2: 双重检查锁（DCL）正确性
 * Validates: Requirements 1.2, 2.2, 3.2
 * 
 * 属性测试：验证双重检查锁（DCL）的正确性
 * 
 * 测试属性：
 * For any 实体ID，当一个请求获取锁成功后，如果缓存已被其他线程填充，
 * 该请求应该直接从缓存读取数据而不查询数据库。
 * 
 * @author ZhiCore Team
 */
@DisplayName("Property 2: 双重检查锁（DCL）正确性属性测试")
class CachedDualStorageManagerDCLPropertyTest {

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
     * Property 2: 双重检查锁（DCL）正确性
     * 
     * For any 随机生成的文章ID，当一个请求获取锁成功后，如果缓存已被其他线程填充，
     * 该请求应该直接从缓存读取数据而不查询数据库。
     * 
     * 验证策略：
     * 1. 生成随机文章ID
     * 2. 模拟第一次缓存检查未命中（进入锁等待）
     * 3. 模拟获取锁后的第二次缓存检查命中（DCL）
     * 4. 验证不会查询数据库
     * 5. 验证返回缓存中的数据
     */
    @Property(tries = 100)
    @DisplayName("Property 2: 获取锁后发现缓存已填充则不查询数据库")
    void testDCL_CacheFilledByOtherThread_NoDatabaseQuery(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存行为 - 第一次未命中，第二次命中（DCL）
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent cachedContent = createTestPostContent(postId);
        
        // 模拟缓存行为：第一次检查未命中，第二次检查命中（DCL）
        AtomicInteger cacheCheckCount = new AtomicInteger(0);
        when(valueOperations.get(cacheKey)).thenAnswer(invocation -> {
            int count = cacheCheckCount.incrementAndGet();
            if (count == 1) {
                // 第一次检查：缓存未命中
                return null;
            } else {
                // 第二次检查（DCL）：缓存已被其他线程填充
                return cachedContent;
            }
        });
        
        // 标记为热点数据
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId)).thenReturn(false);
        
        // 配置锁行为：能够成功获取锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 模拟数据库查询（不应该被调用）
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        when(delegate.getPostContent(postId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            return createTestPostContent(postId);
        });
        
        // When: 调用查询方法
        PostContent result = cachedManager.getPostContent(postId);
        
        // Then: 验证结果
        assertNotNull(result, "应该返回缓存中的数据");
        assertEquals(cachedContent.getPostId(), result.getPostId(), "返回的数据应该是缓存中的数据");
        
        // 核心验证：数据库不应该被查询
        assertEquals(0, databaseQueryCount.get(),
                String.format("数据库不应该被查询，实际查询次数: %d", databaseQueryCount.get()));
        
        // 验证：缓存被检查了两次（第一次 + DCL）
        assertEquals(2, cacheCheckCount.get(),
                String.format("缓存应该被检查两次（第一次 + DCL），实际检查次数: %d", cacheCheckCount.get()));
        
        // 验证：数据库查询方法没有被调用
        verify(delegate, never()).getPostContent(postId);
        
        // 验证：锁被正确释放
        verify(lock, times(1)).unlock();
    }

    /**
     * Property 2 变体：多线程场景下的 DCL 正确性
     * 
     * 当多个线程并发访问时，第一个线程填充缓存后，
     * 后续获取锁的线程应该通过 DCL 从缓存读取，不查询数据库。
     */
    @Property(tries = 100)
    @DisplayName("Property 2 变体: 多线程场景下 DCL 避免重复查询")
    void testDCL_MultipleThreads_OnlyFirstQueriesDatabase(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存和锁行为
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        // 模拟缓存行为：
        // - 第一个线程：第一次未命中，第二次也未命中（需要查询数据库）
        // - 后续线程：第一次未命中，第二次命中（DCL 生效）
        AtomicInteger cacheCheckCount = new AtomicInteger(0);
        AtomicInteger cacheFillCount = new AtomicInteger(0);
        
        when(valueOperations.get(cacheKey)).thenAnswer(invocation -> {
            int count = cacheCheckCount.incrementAndGet();
            if (count <= 2) {
                // 第一个线程的两次检查都未命中
                return null;
            } else {
                // 后续线程的 DCL 检查命中
                return dbContent;
            }
        });
        
        // 模拟缓存写入
        doAnswer(invocation -> {
            cacheFillCount.incrementAndGet();
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        
        // 所有线程都能获取锁（串行获取）
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 模拟数据库查询
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        when(delegate.getPostContent(postId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            // 模拟数据库查询耗时
            Thread.sleep(10);
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
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        
        // Then: 验证结果
        assertTrue(completed, "所有线程应该在5秒内完成");
        
        // 核心验证：只有第一个线程查询了数据库
        assertEquals(1, databaseQueryCount.get(),
                String.format("只有第一个线程应该查询数据库，实际查询次数: %d", databaseQueryCount.get()));
        
        // 验证：所有请求都成功
        assertEquals(concurrentThreads, successCount.get(),
                String.format("所有请求都应该成功，实际成功: %d", successCount.get()));
        
        // 验证：缓存只被填充一次
        assertEquals(1, cacheFillCount.get(),
                String.format("缓存只应该被填充一次，实际填充次数: %d", cacheFillCount.get()));
        
        // 验证：数据库只被查询一次
        verify(delegate, times(1)).getPostContent(postId);
    }

    /**
     * Property 2 变体：验证 DCL 在非热点数据场景下不生效
     * 
     * 对于非热点数据，不使用分布式锁，因此 DCL 不生效。
     * 这个测试验证非热点数据的正常流程。
     */
    @Property(tries = 100)
    @DisplayName("Property 2 变体: 非热点数据不使用 DCL")
    void testDCL_NonHotData_DirectDatabaseQuery(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置为非热点数据
        String cacheKey = PostRedisKeys.content(postId);
        PostContent dbContent = createTestPostContent(postId);
        
        // 缓存未命中
        when(valueOperations.get(cacheKey)).thenReturn(null);
        
        // 标记为非热点数据
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(false);
        
        // 模拟数据库查询
        when(delegate.getPostContent(postId)).thenReturn(dbContent);
        
        // When: 调用查询方法
        PostContent result = cachedManager.getPostContent(postId);
        
        // Then: 验证结果
        assertNotNull(result, "应该返回数据库中的数据");
        assertEquals(dbContent.getPostId(), result.getPostId(), "返回的数据应该是数据库中的数据");
        
        // 验证：不应该尝试获取锁（因为不是热点数据）
        verify(redissonClient, never()).getLock(anyString());
        
        // 验证：直接查询数据库
        verify(delegate, times(1)).getPostContent(postId);
    }

    /**
     * Property 2 变体：验证 DCL 在缓存命中时的快速路径
     * 
     * 当第一次缓存检查就命中时，不应该进入锁逻辑。
     */
    @Property(tries = 100)
    @DisplayName("Property 2 变体: 缓存命中时不进入锁逻辑")
    void testDCL_CacheHit_NoLockAcquisition(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 配置缓存命中
        String cacheKey = PostRedisKeys.content(postId);
        PostContent cachedContent = createTestPostContent(postId);
        
        // 第一次缓存检查就命中
        when(valueOperations.get(cacheKey)).thenReturn(cachedContent);
        
        // 标记为热点数据（但因为缓存命中，不会进入锁逻辑）
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        
        // When: 调用查询方法
        PostContent result = cachedManager.getPostContent(postId);
        
        // Then: 验证结果
        assertNotNull(result, "应该返回缓存中的数据");
        assertEquals(cachedContent.getPostId(), result.getPostId(), "返回的数据应该是缓存中的数据");
        
        // 核心验证：不应该尝试获取锁
        verify(redissonClient, never()).getLock(anyString());
        
        // 验证：不应该查询数据库
        verify(delegate, never()).getPostContent(postId);
        
        // 验证：缓存只被检查一次
        verify(valueOperations, times(1)).get(cacheKey);
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
