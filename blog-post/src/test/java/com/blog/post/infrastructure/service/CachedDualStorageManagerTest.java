package com.blog.post.infrastructure.service;

import com.blog.common.cache.CacheConstants;
import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.post.domain.model.Post;
import com.blog.post.domain.service.DualStorageManager;
import com.blog.post.infrastructure.cache.PostRedisKeys;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缓存击穿防护单元测试
 * 
 * 测试场景：
 * 1. 缓存命中场景
 * 2. 缓存未命中场景
 * 3. 锁超时降级
 * 4. 空值缓存
 * 5. 异常处理
 * 
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CachedDualStorageManager 缓存击穿防护测试")
class CachedDualStorageManagerTest {

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

    private static final Long TEST_POST_ID = 1L;
    private static final String ENTITY_TYPE_POST = "post";

    @BeforeEach
    void setUp() {
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

    // ==================== 缓存命中场景测试 ====================

    @Test
    @DisplayName("测试缓存命中 - getPostContent 返回缓存数据")
    void testGetPostContent_CacheHit() {
        // Given: 缓存中存在数据
        PostContent cachedContent = createTestPostContent();
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        when(valueOperations.get(cacheKey)).thenReturn(cachedContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 返回缓存数据，不查询数据库
        assertNotNull(result);
        assertEquals(cachedContent.getPostId(), result.getPostId());
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).getPostContent(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试缓存命中 - getPostFullDetail 返回缓存数据")
    void testGetPostFullDetail_CacheHit() {
        // Given: 缓存中存在数据
        DualStorageManager.PostDetail cachedDetail = createTestPostDetail();
        String cacheKey = PostRedisKeys.fullDetail(TEST_POST_ID);
        when(valueOperations.get(cacheKey)).thenReturn(cachedDetail);

        // When: 调用 getPostFullDetail
        DualStorageManager.PostDetail result = cachedManager.getPostFullDetail(TEST_POST_ID);

        // Then: 返回缓存数据，不查询数据库
        assertNotNull(result);
        assertEquals(cachedDetail.getPost().getId(), result.getPost().getId());
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).getPostFullDetail(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试空值缓存命中 - getPostContent 返回 null")
    void testGetPostContent_NullValueCacheHit() {
        // Given: 缓存中存在空值标记
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        when(valueOperations.get(cacheKey)).thenReturn(CacheConstants.NULL_VALUE);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 返回 null，不查询数据库
        assertNull(result);
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).getPostContent(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试空值缓存命中 - getPostFullDetail 返回 null")
    void testGetPostFullDetail_NullValueCacheHit() {
        // Given: 缓存中存在空值标记
        String cacheKey = PostRedisKeys.fullDetail(TEST_POST_ID);
        when(valueOperations.get(cacheKey)).thenReturn(CacheConstants.NULL_VALUE);

        // When: 调用 getPostFullDetail
        DualStorageManager.PostDetail result = cachedManager.getPostFullDetail(TEST_POST_ID);

        // Then: 返回 null，不查询数据库
        assertNull(result);
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).getPostFullDetail(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    // ==================== 缓存未命中场景测试 ====================

    @Test
    @DisplayName("测试缓存未命中 - 非热点数据直接查询数据库")
    void testGetPostContent_CacheMiss_NonHotData() {
        // Given: 缓存未命中，非热点数据
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 查询数据库并缓存结果
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(hotDataIdentifier, times(1)).recordAccess(ENTITY_TYPE_POST, TEST_POST_ID);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(dbContent), anyLong(), eq(TimeUnit.SECONDS));
        verify(redissonClient, never()).getLock(any());
    }

    @Test
    @DisplayName("测试缓存未命中 - 热点数据使用分布式锁")
    void testGetPostContent_CacheMiss_HotData_WithLock() throws InterruptedException {
        // Given: 缓存未命中，热点数据
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次检查：未命中
                .thenReturn(null); // DCL 第二次检查：仍未命中
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 使用分布式锁，查询数据库并缓存
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(hotDataIdentifier, times(1)).recordAccess(ENTITY_TYPE_POST, TEST_POST_ID);
        verify(redissonClient, times(1)).getLock(lockKey);
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(dbContent), anyLong(), eq(TimeUnit.SECONDS));
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试缓存未命中 - DCL 双重检查生效")
    void testGetPostContent_CacheMiss_DCL_CacheFilledByOtherThread() throws InterruptedException {
        // Given: 第一次检查未命中，获取锁后第二次检查命中（其他线程已填充）
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent cachedContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey))
                .thenReturn(null)           // 第一次检查：未命中
                .thenReturn(cachedContent); // DCL 第二次检查：命中
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 返回缓存数据，不查询数据库
        assertNotNull(result);
        assertEquals(cachedContent.getPostId(), result.getPostId());
        verify(valueOperations, times(2)).get(cacheKey); // 两次检查
        verify(delegate, never()).getPostContent(any()); // 不查询数据库
        verify(lock, times(1)).unlock();
    }

    // ==================== 锁超时降级测试 ====================

    @Test
    @DisplayName("测试锁超时降级 - 获取锁超时直接查询数据库")
    void testGetPostContent_LockTimeout_Fallback() throws InterruptedException {
        // Given: 获取锁超时
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(false); // 超时
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库，不缓存结果
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        verify(lock, never()).unlock(); // 未获取锁，不需要释放
    }

    @Test
    @DisplayName("测试锁中断降级 - 线程中断时直接查询数据库")
    void testGetPostContent_LockInterrupted_Fallback() throws InterruptedException {
        // Given: 获取锁时线程被中断
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Thread interrupted"));
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        assertTrue(Thread.interrupted()); // 验证中断标志被恢复
    }

    // ==================== 空值缓存测试 ====================

    @Test
    @DisplayName("测试空值缓存 - 数据库返回 null 时缓存空值")
    void testGetPostContent_NullValue_Cached() {
        // Given: 缓存未命中，数据库返回 null
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(null);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 返回 null，并缓存空值
        assertNull(result);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        verify(valueOperations, times(1)).set(
                eq(cacheKey),
                eq(CacheConstants.NULL_VALUE),
                eq(CacheConstants.NULL_VALUE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试空值缓存 - 热点数据使用锁时也缓存空值")
    void testGetPostContent_NullValue_HotData_Cached() throws InterruptedException {
        // Given: 热点数据，数据库返回 null
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(null);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 返回 null，并缓存空值
        assertNull(result);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        verify(valueOperations, times(1)).set(
                eq(cacheKey),
                eq(CacheConstants.NULL_VALUE),
                eq(CacheConstants.NULL_VALUE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
        verify(lock, times(1)).unlock();
    }

    // ==================== 异常处理测试 ====================

    @Test
    @DisplayName("测试 Redis 连接失败降级 - 直接查询数据库")
    void testGetPostContent_RedisConnectionFailed_Fallback() {
        // Given: Redis 连接失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis connection failed"));
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试数据库查询失败 - 锁被正确释放")
    void testGetPostContent_DatabaseQueryFailed_LockReleased() throws InterruptedException {
        // Given: 数据库查询失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID)).thenThrow(new RuntimeException("Database query failed"));

        // When & Then: 调用 getPostContent 抛出异常
        assertThrows(RuntimeException.class, () -> cachedManager.getPostContent(TEST_POST_ID));
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试锁释放失败 - 不影响业务流程")
    void testGetPostContent_LockReleaseFailed_NoImpact() throws InterruptedException {
        // Given: 锁释放失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);
        doThrow(new RuntimeException("Lock release failed")).when(lock).unlock();

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回结果，锁释放失败不影响业务
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试缓存写入失败 - 不影响业务流程")
    void testGetPostContent_CacheWriteFailed_NoImpact() {
        // Given: 缓存写入失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回结果，缓存写入失败不影响业务
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    // ==================== 辅助方法 ====================

    private PostContent createTestPostContent() {
        PostContent content = new PostContent();
        content.setPostId(String.valueOf(TEST_POST_ID));
        content.setRaw("Test raw content");
        content.setHtml("<p>Test html content</p>");
        return content;
    }

    private DualStorageManager.PostDetail createTestPostDetail() {
        Post post = Post.createDraft(TEST_POST_ID, 1L, "Test Post");
        
        PostContent content = createTestPostContent();
        
        return new DualStorageManager.PostDetail(post, content);
    }
}
