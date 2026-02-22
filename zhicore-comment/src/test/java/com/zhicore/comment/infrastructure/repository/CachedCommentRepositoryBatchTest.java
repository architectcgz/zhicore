package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * 评论批量查询优化单元测试
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
@DisplayName("CachedCommentRepository Batch Query Tests")
class CachedCommentRepositoryBatchTest {

    @Mock
    private CommentRepository delegate;

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
    
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private CachedCommentRepository cachedCommentRepository;

    private Comment testComment1;
    private Comment testComment2;
    private Comment testComment3;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Mock nested configuration objects
        CacheProperties.Lock lockConfig = new CacheProperties.Lock();
        lockConfig.setWaitTime(5L);
        lockConfig.setLeaseTime(10L);
        lockConfig.setFair(false);
        lenient().when(cacheProperties.getLock()).thenReturn(lockConfig);
        
        CacheProperties.Ttl ttlConfig = new CacheProperties.Ttl();
        ttlConfig.setNullValue(60L);
        ttlConfig.setEntityDetail(600L);
        lenient().when(cacheProperties.getTtl()).thenReturn(ttlConfig);

        // 创建测试评论
        testComment1 = createTestComment(1L, 100L, 1L, "Comment 1");
        testComment2 = createTestComment(2L, 100L, 2L, "Comment 2");
        testComment3 = createTestComment(3L, 100L, 3L, "Comment 3");
        
        // Setup lenient mocks for hot data identifier (may not be used in all tests)
        lenient().when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        lenient().when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        lenient().doNothing().when(hotDataIdentifier).recordAccess(anyString(), anyLong());
        
        // Setup ObjectMapper to return the same object (identity conversion for tests)
        lenient().when(objectMapper.convertValue(any(), eq(Comment.class))).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof Comment) {
                return arg;
            }
            return null;
        });
    }

    private Comment createTestComment(Long id, Long postId, Long userId, String content) {
        return Comment.createTopLevel(id, postId, userId, content, null, null, null);
    }

    @Test
    @DisplayName("测试全部缓存命中场景 - 应该直接从缓存返回，不查询数据库")
    void testBatchQuery_AllCacheHit() {
        // Given: 所有评论都在缓存中
        Set<Long> commentIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(CommentRedisKeys.detail(1L))).thenReturn(testComment1);
        when(valueOperations.get(CommentRedisKeys.detail(2L))).thenReturn(testComment2);
        when(valueOperations.get(CommentRedisKeys.detail(3L))).thenReturn(testComment3);

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该返回所有评论
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(testComment1));
        assertTrue(result.contains(testComment2));
        assertTrue(result.contains(testComment3));

        // 验证：不应该查询数据库
        verify(delegate, never()).findByIds(any());
        
        // 验证：不应该获取锁
        verify(redissonClient, never()).getLock(anyString());
        
        // 验证：不应该记录热点数据访问
        verify(hotDataIdentifier, never()).recordAccess(anyString(), anyLong());
    }

    @Test
    @DisplayName("测试部分缓存命中场景 - 应该只查询未命中的数据")
    void testBatchQuery_PartialCacheHit() {
        // Given: comment1 和 comment2 在缓存中，comment3 不在
        Set<Long> commentIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(CommentRedisKeys.detail(1L))).thenReturn(testComment1);
        when(valueOperations.get(CommentRedisKeys.detail(2L))).thenReturn(testComment2);
        when(valueOperations.get(CommentRedisKeys.detail(3L))).thenReturn(null);  // 缓存未命中
        
        // comment3 是非热点数据
        when(hotDataIdentifier.isHotData("comment", 3L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot("comment", 3L)).thenReturn(false);
        
        // 数据库返回 comment3
        when(delegate.findByIds(Set.of(3L))).thenReturn(List.of(testComment3));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该返回所有评论
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(testComment1));
        assertTrue(result.contains(testComment2));
        assertTrue(result.contains(testComment3));

        // 验证：只查询未命中的ID
        verify(delegate, times(1)).findByIds(Set.of(3L));
        
        // 验证：应该记录 comment3 的访问
        verify(hotDataIdentifier, times(1)).recordAccess("comment", 3L);
        
        // 验证：应该缓存 comment3
        verify(valueOperations, times(1)).set(
            eq(CommentRedisKeys.detail(3L)),
            eq(testComment3),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试全部缓存未命中场景 - 应该批量查询数据库")
    void testBatchQuery_AllCacheMiss() {
        // Given: 所有评论都不在缓存中
        Set<Long> commentIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 所有评论都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库返回所有评论
        when(delegate.findByIds(commentIds)).thenReturn(List.of(testComment1, testComment2, testComment3));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该返回所有评论
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(testComment1));
        assertTrue(result.contains(testComment2));
        assertTrue(result.contains(testComment3));

        // 验证：应该批量查询数据库
        verify(delegate, times(1)).findByIds(commentIds);
        
        // 验证：应该记录所有评论的访问
        verify(hotDataIdentifier, times(1)).recordAccess("comment", 1L);
        verify(hotDataIdentifier, times(1)).recordAccess("comment", 2L);
        verify(hotDataIdentifier, times(1)).recordAccess("comment", 3L);
        
        // 验证：应该缓存所有评论
        verify(valueOperations, times(3)).set(
            anyString(),
            any(Comment.class),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试热点数据使用分布式锁 - 应该按ID排序避免死锁")
    void testBatchQuery_HotDataWithLock() throws InterruptedException {
        // Given: 所有评论都不在缓存中，且都是热点数据
        Set<Long> commentIds = Set.of(3L, 1L, 2L);  // 故意乱序
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 所有评论都是热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库返回评论
        when(delegate.findById(1L)).thenReturn(Optional.of(testComment1));
        when(delegate.findById(2L)).thenReturn(Optional.of(testComment2));
        when(delegate.findById(3L)).thenReturn(Optional.of(testComment3));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该返回所有评论
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证：应该按排序后的顺序获取锁（1, 2, 3）
        // 这是死锁避免的关键：所有线程都按相同顺序获取锁
        verify(redissonClient, times(1)).getLock(CommentRedisKeys.lockDetail(1L));
        verify(redissonClient, times(1)).getLock(CommentRedisKeys.lockDetail(2L));
        verify(redissonClient, times(1)).getLock(CommentRedisKeys.lockDetail(3L));
        
        // 验证：锁应该被释放
        verify(lock, times(3)).unlock();
    }

    @Test
    @DisplayName("测试混合场景 - 热点数据和非热点数据混合")
    void testBatchQuery_MixedHotAndCold() throws InterruptedException {
        // Given: comment1 是热点，comment2 和 comment3 是非热点
        Set<Long> commentIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // comment1 是热点数据
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(hotDataIdentifier.isHotData("comment", 2L)).thenReturn(false);
        when(hotDataIdentifier.isHotData("comment", 3L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 配置锁（仅 comment1 使用）
        when(redissonClient.getLock(CommentRedisKeys.lockDetail(1L))).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库返回评论
        when(delegate.findById(1L)).thenReturn(Optional.of(testComment1));
        when(delegate.findByIds(Set.of(2L, 3L))).thenReturn(List.of(testComment2, testComment3));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该返回所有评论
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证：热点数据使用锁
        verify(redissonClient, times(1)).getLock(CommentRedisKeys.lockDetail(1L));
        verify(delegate, times(1)).findById(1L);
        
        // 验证：非热点数据批量查询
        verify(delegate, times(1)).findByIds(Set.of(2L, 3L));
        
        // 验证：锁应该被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试空值缓存 - 不存在的评论应该被缓存为空值")
    void testBatchQuery_NullValueCache() {
        // Given: comment1 存在，comment2 不存在
        Set<Long> commentIds = Set.of(1L, 2L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库只返回 comment1
        when(delegate.findByIds(commentIds)).thenReturn(List.of(testComment1));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该只返回 comment1
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(testComment1));

        // 验证：comment1 应该被缓存
        verify(valueOperations, times(1)).set(
            eq(CommentRedisKeys.detail(1L)),
            eq(testComment1),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
        
        // 验证：comment2 应该被缓存为空值
        verify(valueOperations, times(1)).set(
            eq(CommentRedisKeys.detail(2L)),
            eq(CacheConstants.NULL_VALUE),
            eq(60L),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试空集合输入 - 应该返回空列表")
    void testBatchQuery_EmptyInput() {
        // When: 传入空集合
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(new HashSet<>());

        // Then: 应该返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证：不应该有任何操作
        verify(valueOperations, never()).get(anyString());
        verify(delegate, never()).findByIds(any());
    }

    @Test
    @DisplayName("测试null输入 - 应该返回空列表")
    void testBatchQuery_NullInput() {
        // When: 传入null
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(null);

        // Then: 应该返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证：不应该有任何操作
        verify(valueOperations, never()).get(anyString());
        verify(delegate, never()).findByIds(any());
    }

    @Test
    @DisplayName("测试缓存异常降级 - Redis异常时应该查询数据库")
    void testBatchQuery_CacheException() {
        // Given: Redis 抛出异常
        Set<Long> commentIds = Set.of(1L, 2L);
        
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));
        
        // 都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库返回评论
        when(delegate.findByIds(commentIds)).thenReturn(List.of(testComment1, testComment2));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该降级查询数据库
        assertNotNull(result);
        assertEquals(2, result.size());

        // 验证：应该查询数据库
        verify(delegate, times(1)).findByIds(commentIds);
    }

    @Test
    @DisplayName("测试数据库异常处理 - 数据库异常时应该返回已缓存的数据")
    void testBatchQuery_DatabaseException() {
        // Given: comment1 在缓存中，comment2 不在缓存中
        Set<Long> commentIds = Set.of(1L, 2L);
        
        when(valueOperations.get(CommentRedisKeys.detail(1L))).thenReturn(testComment1);
        when(valueOperations.get(CommentRedisKeys.detail(2L))).thenReturn(null);
        
        // comment2 是非热点数据
        when(hotDataIdentifier.isHotData("comment", 2L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot("comment", 2L)).thenReturn(false);
        
        // 数据库抛出异常
        when(delegate.findByIds(Set.of(2L))).thenThrow(new RuntimeException("Database connection failed"));

        // When: 批量查询
        List<Comment> result = cachedCommentRepository.findByIdsWithCache(commentIds);

        // Then: 应该返回缓存中的数据
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(testComment1));
    }
}
