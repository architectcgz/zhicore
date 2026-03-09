package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.model.CommentStatus;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 评论服务缓存击穿防护单元测试
 * 
 * 测试场景：
 * 1. 缓存命中场景
 * 2. 缓存未命中场景
 * 3. 锁超时降级
 * 4. 空值缓存
 * 5. 异常处理
 * 
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CachedCommentRepository 缓存击穿防护测试")
class CachedCommentRepositoryTest {

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
    private CommentRepository delegate;

    @Mock
    private ObjectMapper objectMapper;

    private CachedCommentRepository cachedRepository;

    private static final Long TEST_COMMENT_ID = 1L;
    private static final Long TEST_POST_ID = 100L;
    private static final Long TEST_AUTHOR_ID = 200L;
    private static final String ENTITY_TYPE_COMMENT = "comment";

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
        when(ttlProperties.getNullValue()).thenReturn(60L);

        // 创建被测试对象
        cachedRepository = new CachedCommentRepository(
                delegate,
                redisTemplate,
                redissonClient,
                cacheProperties,
                hotDataIdentifier,
                objectMapper
        );
    }

    // ==================== 缓存命中场景测试 ====================

    @Test
    @DisplayName("测试缓存命中 - findById 返回缓存数据")
    void testFindById_CacheHit() {
        // Given: 缓存中存在数据
        Comment cachedComment = createTestComment();
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        when(valueOperations.get(cacheKey)).thenReturn(cachedComment);
        when(objectMapper.convertValue(cachedComment, Comment.class)).thenReturn(cachedComment);

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 返回缓存数据，不查询数据库
        assertTrue(result.isPresent());
        assertEquals(cachedComment.getId(), result.get().getId());
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).findById(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试空值缓存命中 - findById 返回 empty")
    void testFindById_NullValueCacheHit() {
        // Given: 缓存中存在空值标记
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        when(valueOperations.get(cacheKey)).thenReturn(CacheConstants.NULL_MARKER);

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 返回 empty，不查询数据库
        assertFalse(result.isPresent());
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).findById(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试历史空值缓存命中 - 兼容旧 NULL 标记")
    void testFindById_LegacyNullValueCacheHit() {
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        when(valueOperations.get(cacheKey)).thenReturn(CacheConstants.LEGACY_NULL_MARKER);

        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        assertFalse(result.isPresent());
        verify(valueOperations, times(1)).get(cacheKey);
        verify(delegate, never()).findById(any());
    }

    // ==================== 缓存未命中场景测试 ====================

    @Test
    @DisplayName("测试缓存未命中 - 非热点数据直接查询数据库")
    void testFindById_CacheMiss_NonHotData() {
        // Given: 缓存未命中，非热点数据
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(false);
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 查询数据库并缓存结果
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(hotDataIdentifier, times(1)).recordAccess(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID);
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(dbComment), anyLong(), eq(TimeUnit.SECONDS));
        verify(redissonClient, never()).getLock(any());
    }

    @Test
    @DisplayName("测试缓存未命中 - 热点数据使用分布式锁")
    void testFindById_CacheMiss_HotData_WithLock() throws InterruptedException {
        // Given: 缓存未命中，热点数据
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次检查：未命中
                .thenReturn(null); // DCL 第二次检查：仍未命中
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 使用分布式锁，查询数据库并缓存
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(hotDataIdentifier, times(1)).recordAccess(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID);
        verify(redissonClient, times(1)).getLock(lockKey);
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(dbComment), anyLong(), eq(TimeUnit.SECONDS));
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试缓存未命中 - DCL 双重检查生效")
    void testFindById_CacheMiss_DCL_CacheFilledByOtherThread() throws InterruptedException {
        // Given: 第一次检查未命中，获取锁后第二次检查命中（其他线程已填充）
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        Comment cachedComment = createTestComment();
        
        when(valueOperations.get(cacheKey))
                .thenReturn(null)           // 第一次检查：未命中
                .thenReturn(cachedComment); // DCL 第二次检查：命中
        when(objectMapper.convertValue(cachedComment, Comment.class)).thenReturn(cachedComment);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 返回缓存数据，不查询数据库
        assertTrue(result.isPresent());
        assertEquals(cachedComment.getId(), result.get().getId());
        verify(valueOperations, times(2)).get(cacheKey); // 两次检查
        verify(delegate, never()).findById(any()); // 不查询数据库
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试缓存未命中 - DCL 检查到空值缓存")
    void testFindById_CacheMiss_DCL_NullValueFilledByOtherThread() throws InterruptedException {
        // Given: 第一次检查未命中，获取锁后第二次检查到空值
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        
        when(valueOperations.get(cacheKey))
                .thenReturn(null)                      // 第一次检查：未命中
                .thenReturn(CacheConstants.LEGACY_NULL_MARKER); // DCL 第二次检查：兼容旧空值
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 返回 empty，不查询数据库
        assertFalse(result.isPresent());
        verify(valueOperations, times(2)).get(cacheKey);
        verify(delegate, never()).findById(any());
        verify(lock, times(1)).unlock();
    }

    // ==================== 锁超时降级测试 ====================

    @Test
    @DisplayName("测试锁超时降级 - 获取锁超时直接查询数据库")
    void testFindById_LockTimeout_Fallback() throws InterruptedException {
        // Given: 获取锁超时
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(false); // 超时
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 降级查询数据库，不缓存结果
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(lock, times(1)).tryLock(5L, 10L, TimeUnit.SECONDS);
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any());
        verify(lock, never()).unlock(); // 未获取锁，不需要释放
    }

    @Test
    @DisplayName("测试锁中断降级 - 线程中断时直接查询数据库")
    void testFindById_LockInterrupted_Fallback() throws InterruptedException {
        // Given: 获取锁时线程被中断
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Thread interrupted"));
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 降级查询数据库
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
        assertTrue(Thread.interrupted()); // 验证中断标志被恢复
    }

    // ==================== 空值缓存测试 ====================

    @Test
    @DisplayName("测试空值缓存 - 数据库返回 empty 时缓存空值")
    void testFindById_NullValue_Cached() {
        // Given: 缓存未命中，数据库返回 empty
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(false);
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.empty());

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 返回 empty，并缓存空值
        assertFalse(result.isPresent());
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
        verify(valueOperations, times(1)).set(
                eq(cacheKey),
                eq(CacheConstants.NULL_MARKER),
                eq(60L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试空值缓存 - 热点数据使用锁时也缓存空值")
    void testFindById_NullValue_HotData_Cached() throws InterruptedException {
        // Given: 热点数据，数据库返回 empty
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.empty());

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 返回 empty，并缓存空值
        assertFalse(result.isPresent());
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
        verify(valueOperations, times(1)).set(
                eq(cacheKey),
                eq(CacheConstants.NULL_MARKER),
                eq(60L),
                eq(TimeUnit.SECONDS)
        );
        verify(lock, times(1)).unlock();
    }

    // ==================== 异常处理测试 ====================

    @Test
    @DisplayName("测试 Redis 连接失败降级 - 直接查询数据库")
    void testFindById_RedisConnectionFailed_Fallback() {
        // Given: Redis 连接失败
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("Redis connection failed"));
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 降级查询数据库
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
    }

    @Test
    @DisplayName("测试数据库查询失败 - 锁被正确释放")
    void testFindById_DatabaseQueryFailed_LockReleased() throws InterruptedException {
        // Given: 数据库查询失败
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_COMMENT_ID)).thenThrow(new RuntimeException("Database query failed"));

        // When & Then: 调用 findById 抛出异常
        assertThrows(RuntimeException.class, () -> cachedRepository.findById(TEST_COMMENT_ID));
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试锁释放失败 - 不影响业务流程")
    void testFindById_LockReleaseFailed_NoImpact() throws InterruptedException {
        // Given: 锁释放失败
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));
        doThrow(new RuntimeException("Lock release failed")).when(lock).unlock();

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 正常返回结果，锁释放失败不影响业务
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试缓存写入失败 - 不影响业务流程")
    void testFindById_CacheWriteFailed_NoImpact() {
        // Given: 缓存写入失败
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(false);
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 正常返回结果，缓存写入失败不影响业务
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
    }

    @Test
    @DisplayName("测试通用异常处理 - 降级查询数据库")
    void testFindById_UnexpectedException_Fallback() throws InterruptedException {
        // Given: 发生未预期的异常
        String cacheKey = CommentRedisKeys.detail(TEST_COMMENT_ID);
        String lockKey = CommentRedisKeys.lockDetail(TEST_COMMENT_ID);
        Comment dbComment = createTestComment();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, TEST_COMMENT_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenThrow(new RuntimeException("Unexpected error"));
        when(delegate.findById(TEST_COMMENT_ID)).thenReturn(Optional.of(dbComment));

        // When: 调用 findById
        Optional<Comment> result = cachedRepository.findById(TEST_COMMENT_ID);

        // Then: 降级查询数据库
        assertTrue(result.isPresent());
        assertEquals(dbComment.getId(), result.get().getId());
        verify(delegate, times(1)).findById(TEST_COMMENT_ID);
    }

    // ==================== 辅助方法 ====================

    private Comment createTestComment() {
        return Comment.reconstitute(
                TEST_COMMENT_ID,
                TEST_POST_ID,
                TEST_AUTHOR_ID,
                "Test comment content",
                null,  // imageIds
                null,  // voiceId
                null,  // voiceDuration
                null,  // parentId
                TEST_COMMENT_ID,  // rootId
                null,  // replyToUserId
                CommentStatus.NORMAL,
                LocalDateTime.now(),
                LocalDateTime.now(),
                CommentStats.empty()
        );
    }
}
