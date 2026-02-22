package com.zhicore.content.infrastructure.service;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import com.zhicore.content.domain.service.DualStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文章批量查询优化单元测试
 * 
 * 测试场景：
 * 1. 全部缓存命中场景
 * 2. 部分缓存命中场景
 * 3. 全部缓存未命中场景
 * 4. 死锁避免机制（ID排序）
 * 
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CachedDualStorageManager Batch Query Tests")
class CachedDualStorageManagerBatchTest {

    @Mock
    private DualStorageManager delegate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RLock lock;

    @InjectMocks
    private CachedDualStorageManager cachedDualStorageManager;

    private PostContent testPost1;
    private PostContent testPost2;
    private PostContent testPost3;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Mock nested configuration objects
        CacheProperties.Lock lockConfig = new CacheProperties.Lock();
        lockConfig.setWaitTime(5L);
        lockConfig.setLeaseTime(10L);
        when(cacheProperties.getLock()).thenReturn(lockConfig);
        
        CacheProperties.Ttl ttlConfig = new CacheProperties.Ttl();
        ttlConfig.setNullValue(60L);
        ttlConfig.setEntityDetail(600L);
        when(cacheProperties.getTtl()).thenReturn(ttlConfig);

        // 创建测试文章
        testPost1 = createTestPost(1L, "Title 1", "Content 1");
        testPost2 = createTestPost(2L, "Title 2", "Content 2");
        testPost3 = createTestPost(3L, "Title 3", "Content 3");
    }

    private PostContent createTestPost(Long id, String title, String content) {
        return PostContent.builder()
            .id(String.valueOf(id))
            .postId(String.valueOf(id))
            .raw(content)
            .build();
    }

    @Test
    @DisplayName("测试全部缓存命中场景 - 应该直接从缓存返回，不查询数据库")
    void testBatchQuery_AllCacheHit() {
        // Given: 所有文章都在缓存中
        Set<Long> postIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(PostRedisKeys.content(1L))).thenReturn(testPost1);
        when(valueOperations.get(PostRedisKeys.content(2L))).thenReturn(testPost2);
        when(valueOperations.get(PostRedisKeys.content(3L))).thenReturn(testPost3);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该返回所有文章
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(testPost1, result.get(1L));
        assertEquals(testPost2, result.get(2L));
        assertEquals(testPost3, result.get(3L));

        // 验证：不应该查询数据库
        verify(delegate, never()).getPostContent(anyLong());
        
        // 验证：不应该获取锁
        verify(redissonClient, never()).getLock(anyString());
        
        // 验证：不应该记录热点数据访问
        verify(hotDataIdentifier, never()).recordAccess(anyString(), anyLong());
    }

    @Test
    @DisplayName("测试部分缓存命中场景 - 应该只查询未命中的数据")
    void testBatchQuery_PartialCacheHit() {
        // Given: post1 和 post2 在缓存中，post3 不在
        Set<Long> postIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(PostRedisKeys.content(1L))).thenReturn(testPost1);
        when(valueOperations.get(PostRedisKeys.content(2L))).thenReturn(testPost2);
        when(valueOperations.get(PostRedisKeys.content(3L))).thenReturn(null);  // 缓存未命中
        
        // post3 是非热点数据
        when(hotDataIdentifier.isHotData("post", 3L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot("post", 3L)).thenReturn(false);
        
        // 数据库返回 post3
        when(delegate.getPostContent(3L)).thenReturn(testPost3);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该返回所有文章
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(testPost1, result.get(1L));
        assertEquals(testPost2, result.get(2L));
        assertEquals(testPost3, result.get(3L));

        // 验证：只查询未命中的ID
        verify(delegate, times(1)).getPostContent(3L);
        verify(delegate, never()).getPostContent(1L);
        verify(delegate, never()).getPostContent(2L);
        
        // 验证：应该记录 post3 的访问
        verify(hotDataIdentifier, times(1)).recordAccess("post", 3L);
        
        // 验证：应该缓存 post3
        verify(valueOperations, times(1)).set(
            eq(PostRedisKeys.content(3L)),
            eq(testPost3),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试全部缓存未命中场景 - 应该查询所有数据")
    void testBatchQuery_AllCacheMiss() {
        // Given: 所有文章都不在缓存中
        Set<Long> postIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 所有文章都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库返回所有文章
        when(delegate.getPostContent(1L)).thenReturn(testPost1);
        when(delegate.getPostContent(2L)).thenReturn(testPost2);
        when(delegate.getPostContent(3L)).thenReturn(testPost3);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该返回所有文章
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(testPost1, result.get(1L));
        assertEquals(testPost2, result.get(2L));
        assertEquals(testPost3, result.get(3L));

        // 验证：应该查询所有文章
        verify(delegate, times(1)).getPostContent(1L);
        verify(delegate, times(1)).getPostContent(2L);
        verify(delegate, times(1)).getPostContent(3L);
        
        // 验证：应该记录所有文章的访问
        verify(hotDataIdentifier, times(1)).recordAccess("post", 1L);
        verify(hotDataIdentifier, times(1)).recordAccess("post", 2L);
        verify(hotDataIdentifier, times(1)).recordAccess("post", 3L);
        
        // 验证：应该缓存所有文章
        verify(valueOperations, times(3)).set(
            anyString(),
            any(PostContent.class),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试热点数据使用分布式锁 - 应该按ID排序避免死锁")
    void testBatchQuery_HotDataWithLock() throws InterruptedException {
        // Given: 所有文章都不在缓存中，且都是热点数据
        Set<Long> postIds = Set.of(3L, 1L, 2L);  // 故意乱序
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 所有文章都是热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库返回文章
        when(delegate.getPostContent(1L)).thenReturn(testPost1);
        when(delegate.getPostContent(2L)).thenReturn(testPost2);
        when(delegate.getPostContent(3L)).thenReturn(testPost3);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该返回所有文章
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证：应该按排序后的顺序获取锁（1, 2, 3）
        // 这是死锁避免的关键：所有线程都按相同顺序获取锁
        verify(redissonClient, times(1)).getLock(PostRedisKeys.lockContent(1L));
        verify(redissonClient, times(1)).getLock(PostRedisKeys.lockContent(2L));
        verify(redissonClient, times(1)).getLock(PostRedisKeys.lockContent(3L));
        
        // 验证：锁应该被释放
        verify(lock, times(3)).unlock();
    }

    @Test
    @DisplayName("测试混合场景 - 热点数据和非热点数据混合")
    void testBatchQuery_MixedHotAndCold() throws InterruptedException {
        // Given: post1 是热点，post2 和 post3 是非热点
        Set<Long> postIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // post1 是热点数据
        when(hotDataIdentifier.isHotData("post", 1L)).thenReturn(true);
        when(hotDataIdentifier.isHotData("post", 2L)).thenReturn(false);
        when(hotDataIdentifier.isHotData("post", 3L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 配置锁（仅 post1 使用）
        when(redissonClient.getLock(PostRedisKeys.lockContent(1L))).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库返回文章
        when(delegate.getPostContent(1L)).thenReturn(testPost1);
        when(delegate.getPostContent(2L)).thenReturn(testPost2);
        when(delegate.getPostContent(3L)).thenReturn(testPost3);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该返回所有文章
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证：热点数据使用锁
        verify(redissonClient, times(1)).getLock(PostRedisKeys.lockContent(1L));
        verify(delegate, times(1)).getPostContent(1L);
        
        // 验证：非热点数据直接查询
        verify(delegate, times(1)).getPostContent(2L);
        verify(delegate, times(1)).getPostContent(3L);
        
        // 验证：锁应该被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试空值缓存 - 不存在的文章应该被缓存为空值")
    void testBatchQuery_NullValueCache() {
        // Given: post1 存在，post2 不存在
        Set<Long> postIds = Set.of(1L, 2L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库只返回 post1
        when(delegate.getPostContent(1L)).thenReturn(testPost1);
        when(delegate.getPostContent(2L)).thenReturn(null);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该只返回 post1
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testPost1, result.get(1L));
        assertNull(result.get(2L));

        // 验证：post1 应该被缓存
        verify(valueOperations, times(1)).set(
            eq(PostRedisKeys.content(1L)),
            eq(testPost1),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
        
        // 验证：post2 应该被缓存为空值
        verify(valueOperations, times(1)).set(
            eq(PostRedisKeys.content(2L)),
            eq(CacheConstants.NULL_VALUE),
            eq(60L),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试空集合输入 - 应该返回空Map")
    void testBatchQuery_EmptyInput() {
        // When: 传入空集合
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(new HashSet<>());

        // Then: 应该返回空Map
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证：不应该有任何操作
        verify(valueOperations, never()).get(anyString());
        verify(delegate, never()).getPostContent(anyLong());
    }

    @Test
    @DisplayName("测试null输入 - 应该返回空Map")
    void testBatchQuery_NullInput() {
        // When: 传入null
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(null);

        // Then: 应该返回空Map
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证：不应该有任何操作
        verify(valueOperations, never()).get(anyString());
        verify(delegate, never()).getPostContent(anyLong());
    }

    @Test
    @DisplayName("测试缓存异常降级 - Redis异常时应该查询数据库")
    void testBatchQuery_CacheException() {
        // Given: Redis 抛出异常
        Set<Long> postIds = Set.of(1L, 2L);
        
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));
        
        // 都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库返回文章
        when(delegate.getPostContent(1L)).thenReturn(testPost1);
        when(delegate.getPostContent(2L)).thenReturn(testPost2);

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该降级查询数据库
        assertNotNull(result);
        assertEquals(2, result.size());

        // 验证：应该查询数据库
        verify(delegate, times(1)).getPostContent(1L);
        verify(delegate, times(1)).getPostContent(2L);
    }

    @Test
    @DisplayName("测试数据库异常处理 - 数据库异常时应该返回已缓存的数据")
    void testBatchQuery_DatabaseException() {
        // Given: post1 在缓存中，post2 不在缓存中
        Set<Long> postIds = Set.of(1L, 2L);
        
        when(valueOperations.get(PostRedisKeys.content(1L))).thenReturn(testPost1);
        when(valueOperations.get(PostRedisKeys.content(2L))).thenReturn(null);
        
        // post2 是非热点数据
        when(hotDataIdentifier.isHotData("post", 2L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot("post", 2L)).thenReturn(false);
        
        // 数据库抛出异常
        when(delegate.getPostContent(2L)).thenThrow(new RuntimeException("Database connection failed"));

        // When: 批量查询
        Map<Long, PostContent> result = cachedDualStorageManager.getPostContentBatch(postIds);

        // Then: 应该返回缓存中的数据
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testPost1, result.get(1L));
    }
}
