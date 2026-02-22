package com.zhicore.content.infrastructure.service;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.service.DualStorageManager;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缓存击穿防护异常处理测试
 * 
 * 测试场景：
 * 1. Redis 连接失败场景
 * 2. 锁释放失败场景
 * 3. 数据库查询失败场景
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4
 * 
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CachedDualStorageManager 异常处理测试")
class CachedDualStorageManagerExceptionTest {

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

    // ==================== Redis 连接失败场景测试 ====================

    @Test
    @DisplayName("测试 Redis 连接失败 - getPostContent 降级查询数据库")
    void testGetPostContent_RedisConnectionFailed_FallbackToDatabase() {
        // Given: Redis 连接失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库，返回正确结果
        assertNotNull(result, "Should return database result when Redis fails");
        assertEquals(dbContent.getPostId(), result.getPostId());
        assertEquals(dbContent.getRaw(), result.getRaw());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试 Redis 连接失败 - getPostFullDetail 降级查询数据库")
    void testGetPostFullDetail_RedisConnectionFailed_FallbackToDatabase() {
        // Given: Redis 连接失败
        String cacheKey = PostRedisKeys.fullDetail(TEST_POST_ID);
        DualStorageManager.PostDetail dbDetail = createTestPostDetail();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        when(delegate.getPostFullDetail(TEST_POST_ID)).thenReturn(dbDetail);

        // When: 调用 getPostFullDetail
        DualStorageManager.PostDetail result = cachedManager.getPostFullDetail(TEST_POST_ID);

        // Then: 降级查询数据库，返回正确结果
        assertNotNull(result, "Should return database result when Redis fails");
        assertEquals(dbDetail.getPost().getId(), result.getPost().getId());
        verify(delegate, times(1)).getPostFullDetail(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试 Redis 超时异常 - 降级查询数据库")
    void testGetPostContent_RedisTimeout_FallbackToDatabase() {
        // Given: Redis 操作超时
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Redis operation timeout"));
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试 Redis 网络异常 - 降级查询数据库")
    void testGetPostContent_RedisNetworkError_FallbackToDatabase() {
        // Given: Redis 网络异常
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Network error: Connection reset"));
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库
        assertNotNull(result);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试 Redis 序列化异常 - 降级查询数据库")
    void testGetPostContent_RedisSerializationError_FallbackToDatabase() {
        // Given: Redis 序列化异常
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Serialization error"));
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 降级查询数据库
        assertNotNull(result);
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    // ==================== 锁释放失败场景测试 ====================

    @Test
    @DisplayName("测试锁释放失败 - 不影响业务流程返回正确结果")
    void testGetPostContent_LockReleaseFailed_ReturnsCorrectResult() throws InterruptedException {
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
        doThrow(new RuntimeException("Lock release failed: Redis connection lost"))
                .when(lock).unlock();

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回结果，锁释放失败不影响业务
        assertNotNull(result, "Should return result even if lock release fails");
        assertEquals(dbContent.getPostId(), result.getPostId());
        assertEquals(dbContent.getRaw(), result.getRaw());
        verify(lock, times(1)).unlock();
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试锁释放失败 - getPostFullDetail 不影响业务流程")
    void testGetPostFullDetail_LockReleaseFailed_ReturnsCorrectResult() throws InterruptedException {
        // Given: 锁释放失败
        String cacheKey = PostRedisKeys.fullDetail(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockFullDetail(TEST_POST_ID);
        DualStorageManager.PostDetail dbDetail = createTestPostDetail();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostFullDetail(TEST_POST_ID)).thenReturn(dbDetail);
        doThrow(new RuntimeException("Lock release failed"))
                .when(lock).unlock();

        // When: 调用 getPostFullDetail
        DualStorageManager.PostDetail result = cachedManager.getPostFullDetail(TEST_POST_ID);

        // Then: 正常返回结果
        assertNotNull(result);
        assertEquals(dbDetail.getPost().getId(), result.getPost().getId());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试锁释放网络异常 - 依赖自动过期机制")
    void testGetPostContent_LockReleaseNetworkError_ReliesOnAutoExpiration() throws InterruptedException {
        // Given: 锁释放时网络异常
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);
        doThrow(new RedisConnectionFailureException("Network error during unlock"))
                .when(lock).unlock();

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回结果，依赖锁的自动过期机制
        assertNotNull(result);
        verify(lock, times(1)).unlock();
    }

    // ==================== 数据库查询失败场景测试 ====================

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
        when(delegate.getPostContent(TEST_POST_ID))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then: 调用 getPostContent 抛出异常
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> cachedManager.getPostContent(TEST_POST_ID));
        
        assertEquals("Database connection failed", exception.getMessage());
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试数据库查询失败 - getPostFullDetail 锁被正确释放")
    void testGetPostFullDetail_DatabaseQueryFailed_LockReleased() throws InterruptedException {
        // Given: 数据库查询失败
        String cacheKey = PostRedisKeys.fullDetail(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockFullDetail(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostFullDetail(TEST_POST_ID))
                .thenThrow(new RuntimeException("Database timeout"));

        // When & Then: 调用 getPostFullDetail 抛出异常
        assertThrows(RuntimeException.class, 
                () -> cachedManager.getPostFullDetail(TEST_POST_ID));
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试数据库超时异常 - 锁被正确释放")
    void testGetPostContent_DatabaseTimeout_LockReleased() throws InterruptedException {
        // Given: 数据库查询超时
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID))
                .thenThrow(new RuntimeException("Query timeout: 30 seconds exceeded"));

        // When & Then: 抛出异常
        assertThrows(RuntimeException.class, 
                () -> cachedManager.getPostContent(TEST_POST_ID));
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试数据库查询失败 - 非热点数据不使用锁")
    void testGetPostContent_DatabaseQueryFailed_NonHotData_NoLock() {
        // Given: 非热点数据，数据库查询失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(delegate.getPostContent(TEST_POST_ID))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then: 抛出异常
        assertThrows(RuntimeException.class, 
                () -> cachedManager.getPostContent(TEST_POST_ID));
        
        // 验证没有使用锁
        verify(redissonClient, never()).getLock(any());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    // ==================== 缓存写入失败场景测试 ====================

    @Test
    @DisplayName("测试缓存写入失败 - 不影响业务流程")
    void testGetPostContent_CacheWriteFailed_ReturnsCorrectResult() {
        // Given: 缓存写入失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);
        doThrow(new RuntimeException("Cache write failed: Redis out of memory"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回结果，缓存写入失败不影响业务
        assertNotNull(result, "Should return result even if cache write fails");
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    @Test
    @DisplayName("测试缓存写入失败 - 热点数据锁被正确释放")
    void testGetPostContent_CacheWriteFailed_HotData_LockReleased() throws InterruptedException {
        // Given: 热点数据，缓存写入失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        PostContent dbContent = createTestPostContent();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(dbContent);
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回结果，锁被释放
        assertNotNull(result);
        assertEquals(dbContent.getPostId(), result.getPostId());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试空值缓存写入失败 - 不影响业务流程")
    void testGetPostContent_NullValueCacheWriteFailed_ReturnsNull() {
        // Given: 空值缓存写入失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(false);
        when(delegate.getPostContent(TEST_POST_ID)).thenReturn(null);
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(eq(cacheKey), eq(CacheConstants.NULL_VALUE), anyLong(), any());

        // When: 调用 getPostContent
        PostContent result = cachedManager.getPostContent(TEST_POST_ID);

        // Then: 正常返回 null
        assertNull(result, "Should return null even if null value cache write fails");
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    // ==================== 复合异常场景测试 ====================

    @Test
    @DisplayName("测试数据库查询失败且锁释放失败 - 抛出数据库异常")
    void testGetPostContent_DatabaseAndLockReleaseFailed_ThrowsDatabaseException() throws InterruptedException {
        // Given: 数据库查询失败，锁释放也失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        String lockKey = PostRedisKeys.lockContent(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, TEST_POST_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.getPostContent(TEST_POST_ID))
                .thenThrow(new RuntimeException("Database failed"));
        doThrow(new RuntimeException("Lock release failed"))
                .when(lock).unlock();

        // When & Then: 抛出数据库异常（主要异常）
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> cachedManager.getPostContent(TEST_POST_ID));
        
        assertEquals("Database failed", exception.getMessage());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试 Redis 连接失败且数据库查询失败 - 抛出数据库异常")
    void testGetPostContent_RedisAndDatabaseFailed_ThrowsDatabaseException() {
        // Given: Redis 连接失败，数据库查询也失败
        String cacheKey = PostRedisKeys.content(TEST_POST_ID);
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RedisConnectionFailureException("Redis failed"));
        when(delegate.getPostContent(TEST_POST_ID))
                .thenThrow(new RuntimeException("Database failed"));

        // When & Then: 抛出数据库异常
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> cachedManager.getPostContent(TEST_POST_ID));
        
        assertEquals("Database failed", exception.getMessage());
        verify(delegate, times(1)).getPostContent(TEST_POST_ID);
    }

    // ==================== 辅助方法 ====================

    private PostContent createTestPostContent() {
        return PostContent.builder()
            .postId(String.valueOf(TEST_POST_ID))
            .raw("Test raw content")
            .html("<p>Test html content</p>")
            .build();
    }

    private DualStorageManager.PostDetail createTestPostDetail() {
        Post post = Post.reconstitute(
            TEST_POST_ID,
            1L,  // ownerId
            "Test Post",
            null,  // excerpt
            null,  // coverImage
            PostStatus.PUBLISHED,
            null,  // topicId
            java.time.LocalDateTime.now(),  // publishedAt
            null,  // scheduledAt
            java.time.LocalDateTime.now(),  // createdAt
            java.time.LocalDateTime.now(),  // updatedAt
            false,  // isArchived
            PostStats.empty()
        );
        
        PostContent content = createTestPostContent();
        
        return new DualStorageManager.PostDetail(post, content);
    }
}
