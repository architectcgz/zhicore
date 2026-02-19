package com.blog.post.infrastructure.service;

import com.blog.common.cache.CacheConstants;
import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.post.domain.service.DualStorageManager;
import com.blog.post.infrastructure.cache.PostRedisKeys;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 3: 缓存填充后的一致性
 * Validates: Requirements 1.3
 * 
 * 属性测试：验证缓存填充后的一致性
 * 
 * 测试属性：
 * For any 实体ID，当第一个请求成功加载数据并写入缓存后，
 * 所有后续请求都应该能够从缓存中读取到相同的数据。
 * 
 * @author Blog Team
 */
@DisplayName("Property 3: 缓存填充后的一致性属性测试")
class CachedDualStorageManagerCacheConsistencyPropertyTest {

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
     * Property 3: 缓存填充后的一致性
     * 
     * For any 随机生成的文章ID和内容，当第一个请求成功加载数据并写入缓存后，
     * 后续所有请求都应该从缓存中读取到相同的数据，且与数据库数据一致。
     * 
     * 验证策略：
     * 1. 生成随机文章ID和内容
     * 2. 第一次查询：缓存未命中，从数据库加载并写入缓存
     * 3. 验证缓存写入的数据与数据库数据一致
     * 4. 第二次查询：从缓存读取
     * 5. 验证缓存读取的数据与数据库数据一致
     */
    @Property(tries = 100)
    @DisplayName("Property 3: 缓存填充后数据与数据库一致")
    void testCacheConsistency_CachedDataMatchesDatabase(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId,
            @ForAll @StringLength(min = 10, max = 100) String rawContent,
            @ForAll @StringLength(min = 10, max = 100) String htmlContent) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        // 创建数据库中的原始数据
        PostContent dbContent = createTestPostContent(postId, rawContent, htmlContent);
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次查询：缓存未命中
                .thenAnswer(invocation -> cachedData.get());  // 第二次查询：返回缓存的数据
        
        // 配置缓存写入：捕获写入的数据
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_POST, postId)).thenReturn(false);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询：返回原始数据
        when(delegate.getPostContent(postId)).thenReturn(dbContent);
        
        // When: 第一次查询（缓存未命中，从数据库加载）
        PostContent firstResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第一次查询结果
        assertNotNull(firstResult, "第一次查询应该返回数据");
        assertEquals(dbContent.getPostId(), firstResult.getPostId(), "文章ID应该一致");
        assertEquals(dbContent.getRaw(), firstResult.getRaw(), "Raw内容应该一致");
        assertEquals(dbContent.getHtml(), firstResult.getHtml(), "HTML内容应该一致");
        
        // 验证数据被写入缓存
        assertNotNull(cachedData.get(), "数据应该被写入缓存");
        
        // 验证缓存中的数据与数据库数据一致
        PostContent cachedContent = (PostContent) cachedData.get();
        assertEquals(dbContent.getPostId(), cachedContent.getPostId(), 
                "缓存中的文章ID应该与数据库一致");
        assertEquals(dbContent.getRaw(), cachedContent.getRaw(), 
                "缓存中的Raw内容应该与数据库一致");
        assertEquals(dbContent.getHtml(), cachedContent.getHtml(), 
                "缓存中的HTML内容应该与数据库一致");
        
        // When: 第二次查询（从缓存读取）
        PostContent secondResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第二次查询结果
        assertNotNull(secondResult, "第二次查询应该返回数据");
        assertEquals(dbContent.getPostId(), secondResult.getPostId(), 
                "第二次查询的文章ID应该与数据库一致");
        assertEquals(dbContent.getRaw(), secondResult.getRaw(), 
                "第二次查询的Raw内容应该与数据库一致");
        assertEquals(dbContent.getHtml(), secondResult.getHtml(), 
                "第二次查询的HTML内容应该与数据库一致");
        
        // 验证两次查询结果一致
        assertEquals(firstResult.getPostId(), secondResult.getPostId(), 
                "两次查询的文章ID应该一致");
        assertEquals(firstResult.getRaw(), secondResult.getRaw(), 
                "两次查询的Raw内容应该一致");
        assertEquals(firstResult.getHtml(), secondResult.getHtml(), 
                "两次查询的HTML内容应该一致");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).getPostContent(postId);
        
        // 验证缓存被写入一次
        verify(valueOperations, times(1)).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 3 变体：验证空值缓存的一致性
     * 
     * 当数据库中不存在数据时，应该缓存空值，后续查询应该返回一致的空值。
     */
    @Property(tries = 100)
    @DisplayName("Property 3 变体: 空值缓存一致性")
    void testCacheConsistency_NullValueConsistency(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次查询：缓存未命中
                .thenAnswer(invocation -> cachedData.get());  // 第二次查询：返回缓存的空值
        
        // 配置缓存写入：捕获写入的数据
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询：返回 null（数据不存在）
        when(delegate.getPostContent(postId)).thenReturn(null);
        
        // When: 第一次查询（缓存未命中，从数据库加载）
        PostContent firstResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第一次查询结果
        assertNull(firstResult, "第一次查询应该返回null");
        
        // 验证空值被写入缓存
        assertNotNull(cachedData.get(), "空值应该被写入缓存");
        assertEquals(CacheConstants.NULL_VALUE, cachedData.get(), 
                "缓存中应该是NULL_VALUE标记");
        
        // When: 第二次查询（从缓存读取空值）
        PostContent secondResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第二次查询结果
        assertNull(secondResult, "第二次查询应该返回null");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).getPostContent(postId);
        
        // 验证缓存被写入一次
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(CacheConstants.NULL_VALUE), 
                anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 3 变体：验证多次查询的一致性
     * 
     * 验证同一个文章ID多次查询返回一致的数据。
     */
    @Property(tries = 100)
    @DisplayName("Property 3 变体: 多次查询一致性")
    void testCacheConsistency_MultipleQueriesConsistency(
            @ForAll @LongRange(min = 1L, max = 100000L) Long postId,
            @ForAll @StringLength(min = 10, max = 100) String rawContent) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = PostRedisKeys.content(postId);
        String lockKey = PostRedisKeys.lockContent(postId);
        
        // 创建数据库中的原始数据
        PostContent dbContent = createTestPostContent(postId, rawContent, "<p>" + rawContent + "</p>");
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次查询：缓存未命中
                .thenAnswer(invocation -> cachedData.get());  // 后续查询：返回缓存的数据
        
        // 配置缓存写入
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_POST, postId)).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询
        when(delegate.getPostContent(postId)).thenReturn(dbContent);
        
        // When: 第一次查询
        PostContent firstResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第一次查询结果与数据库一致
        assertNotNull(firstResult, "第一次查询应该返回数据");
        assertEquals(dbContent.getPostId(), firstResult.getPostId(), "文章ID应该一致");
        assertEquals(dbContent.getRaw(), firstResult.getRaw(), "Raw内容应该一致");
        
        // 验证缓存数据与数据库一致
        assertNotNull(cachedData.get(), "数据应该被写入缓存");
        PostContent cachedContent = (PostContent) cachedData.get();
        assertEquals(dbContent.getPostId(), cachedContent.getPostId(), 
                "缓存中的数据应该与数据库一致");
        
        // When: 第二次查询
        PostContent secondResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第二次查询结果与第一次一致
        assertNotNull(secondResult, "第二次查询应该返回数据");
        assertEquals(firstResult.getPostId(), secondResult.getPostId(), 
                "两次查询结果应该一致");
        assertEquals(firstResult.getRaw(), secondResult.getRaw(), 
                "两次查询的内容应该一致");
        
        // When: 第三次查询
        PostContent thirdResult = cachedManager.getPostContent(postId);
        
        // Then: 验证第三次查询结果与前两次一致
        assertNotNull(thirdResult, "第三次查询应该返回数据");
        assertEquals(firstResult.getPostId(), thirdResult.getPostId(), 
                "第三次查询结果应该与第一次一致");
        assertEquals(secondResult.getPostId(), thirdResult.getPostId(), 
                "第三次查询结果应该与第二次一致");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).getPostContent(postId);
    }

    // ==================== 辅助方法 ====================

    private PostContent createTestPostContent(Long postId, String rawContent, String htmlContent) {
        PostContent content = new PostContent();
        content.setPostId(String.valueOf(postId));
        content.setRaw(rawContent);
        content.setHtml(htmlContent);
        return content;
    }
}
