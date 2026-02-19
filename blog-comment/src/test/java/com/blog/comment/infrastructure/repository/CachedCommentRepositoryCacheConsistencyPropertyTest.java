package com.blog.comment.infrastructure.repository;

import com.blog.common.cache.CacheConstants;
import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.comment.domain.model.Comment;
import com.blog.comment.domain.model.CommentStats;
import com.blog.comment.domain.repository.CommentRepository;
import com.blog.comment.infrastructure.cache.CommentRedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 3: 缓存填充后的一致性
 * Validates: Requirements 1.3
 * 
 * 属性测试：验证评论服务缓存填充后的一致性
 * 
 * 测试属性：
 * For any 评论ID，当第一个请求成功加载数据并写入缓存后，
 * 所有后续请求都应该能够从缓存中读取到相同的数据。
 * 
 * @author Blog Team
 */
@DisplayName("Property 3: 评论服务缓存一致性属性测试")
class CachedCommentRepositoryCacheConsistencyPropertyTest {

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

    private CachedCommentRepository cachedRepository;
    
    private ObjectMapper objectMapper;

    private static final String ENTITY_TYPE_COMMENT = "comment";

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
        when(ttlProperties.getNullValue()).thenReturn(60L);

        // 创建 ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // 创建被测试对象 - 注意参数顺序
        cachedRepository = new CachedCommentRepository(
                delegate,
                redisTemplate,
                redissonClient,
                cacheProperties,
                hotDataIdentifier,
                objectMapper
        );
    }

    /**
     * Property 3: 缓存填充后的一致性
     * 
     * For any 随机生成的评论ID和评论数据，当第一个请求成功加载数据并写入缓存后，
     * 后续所有请求都应该从缓存中读取到相同的数据，且与数据库数据一致。
     */
    @Property(tries = 100)
    @DisplayName("Property 3: 缓存填充后数据与数据库一致")
    void testCacheConsistency_CachedDataMatchesDatabase(
            @ForAll @LongRange(min = 1L, max = 100000L) Long commentId,
            @ForAll @LongRange(min = 1L, max = 10000L) Long postId,
            @ForAll @LongRange(min = 1L, max = 10000L) Long userId,
            @ForAll @StringLength(min = 10, max = 200) String content) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = CommentRedisKeys.detail(commentId);
        String lockKey = CommentRedisKeys.lockDetail(commentId);
        
        // 创建数据库中的原始数据
        Comment dbComment = createTestComment(commentId, postId, userId, content);
        
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
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, commentId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_COMMENT, commentId)).thenReturn(false);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询：返回原始数据
        when(delegate.findById(commentId)).thenReturn(Optional.of(dbComment));
        
        // When: 第一次查询（缓存未命中，从数据库加载）
        Optional<Comment> firstResult = cachedRepository.findById(commentId);
        
        // Then: 验证第一次查询结果
        assertTrue(firstResult.isPresent(), "第一次查询应该返回数据");
        Comment firstComment = firstResult.get();
        assertEquals(dbComment.getId(), firstComment.getId(), "评论ID应该一致");
        assertEquals(dbComment.getPostId(), firstComment.getPostId(), "文章ID应该一致");
        assertEquals(dbComment.getAuthorId(), firstComment.getAuthorId(), "用户ID应该一致");
        assertEquals(dbComment.getContent(), firstComment.getContent(), "内容应该一致");
        
        // 验证数据被写入缓存
        assertNotNull(cachedData.get(), "数据应该被写入缓存");
        
        // 验证缓存中的数据与数据库数据一致
        Comment cachedComment = (Comment) cachedData.get();
        assertEquals(dbComment.getId(), cachedComment.getId(), 
                "缓存中的评论ID应该与数据库一致");
        assertEquals(dbComment.getPostId(), cachedComment.getPostId(), 
                "缓存中的文章ID应该与数据库一致");
        assertEquals(dbComment.getAuthorId(), cachedComment.getAuthorId(), 
                "缓存中的用户ID应该与数据库一致");
        assertEquals(dbComment.getContent(), cachedComment.getContent(), 
                "缓存中的内容应该与数据库一致");
        
        // When: 第二次查询（从缓存读取）
        Optional<Comment> secondResult = cachedRepository.findById(commentId);
        
        // Then: 验证第二次查询结果
        assertTrue(secondResult.isPresent(), "第二次查询应该返回数据");
        Comment secondComment = secondResult.get();
        assertEquals(dbComment.getId(), secondComment.getId(), 
                "第二次查询的评论ID应该与数据库一致");
        assertEquals(dbComment.getPostId(), secondComment.getPostId(), 
                "第二次查询的文章ID应该与数据库一致");
        assertEquals(dbComment.getAuthorId(), secondComment.getAuthorId(), 
                "第二次查询的用户ID应该与数据库一致");
        assertEquals(dbComment.getContent(), secondComment.getContent(), 
                "第二次查询的内容应该与数据库一致");
        
        // 验证两次查询结果一致
        assertEquals(firstComment.getId(), secondComment.getId(), 
                "两次查询的评论ID应该一致");
        assertEquals(firstComment.getPostId(), secondComment.getPostId(), 
                "两次查询的文章ID应该一致");
        assertEquals(firstComment.getAuthorId(), secondComment.getAuthorId(), 
                "两次查询的用户ID应该一致");
        assertEquals(firstComment.getContent(), secondComment.getContent(), 
                "两次查询的内容应该一致");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).findById(commentId);
        
        // 验证缓存被写入一次
        verify(valueOperations, times(1)).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 3 变体：验证空值缓存的一致性
     * 
     * 当数据库中不存在评论时，应该缓存空值，后续查询应该返回一致的空值。
     */
    @Property(tries = 100)
    @DisplayName("Property 3 变体: 空值缓存一致性")
    void testCacheConsistency_NullValueConsistency(
            @ForAll @LongRange(min = 1L, max = 100000L) Long commentId) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = CommentRedisKeys.detail(commentId);
        String lockKey = CommentRedisKeys.lockDetail(commentId);
        
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
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, commentId)).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询：返回空（评论不存在）
        when(delegate.findById(commentId)).thenReturn(Optional.empty());
        
        // When: 第一次查询（缓存未命中，从数据库加载）
        Optional<Comment> firstResult = cachedRepository.findById(commentId);
        
        // Then: 验证第一次查询结果
        assertFalse(firstResult.isPresent(), "第一次查询应该返回空");
        
        // 验证空值被写入缓存
        assertNotNull(cachedData.get(), "空值应该被写入缓存");
        assertEquals(CacheConstants.NULL_VALUE, cachedData.get(), 
                "缓存中应该是NULL_VALUE标记");
        
        // When: 第二次查询（从缓存读取空值）
        Optional<Comment> secondResult = cachedRepository.findById(commentId);
        
        // Then: 验证第二次查询结果
        assertFalse(secondResult.isPresent(), "第二次查询应该返回空");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).findById(commentId);
        
        // 验证缓存被写入一次
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(CacheConstants.NULL_VALUE), 
                anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 3 变体：验证包含统计信息的评论数据一致性
     * 
     * 验证评论对象及其统计信息在缓存和数据库之间保持一致。
     */
    @Property(tries = 100)
    @DisplayName("Property 3 变体: 包含统计信息的数据一致性")
    void testCacheConsistency_WithStatsConsistency(
            @ForAll @LongRange(min = 1L, max = 100000L) Long commentId,
            @ForAll @LongRange(min = 1L, max = 10000L) Long postId,
            @ForAll @LongRange(min = 1L, max = 10000L) Long userId,
            @ForAll @StringLength(min = 10, max = 200) String content,
            @ForAll @LongRange(min = 0L, max = 1000L) Long likeCount,
            @ForAll @LongRange(min = 0L, max = 100L) Long replyCount) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = CommentRedisKeys.detail(commentId);
        String lockKey = CommentRedisKeys.lockDetail(commentId);
        
        // 创建包含统计信息的评论数据
        CommentStats stats = new CommentStats(likeCount.intValue(), replyCount.intValue());
        Comment dbComment = createTestCommentWithStats(commentId, postId, userId, content, stats);
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)
                .thenAnswer(invocation -> cachedData.get());
        
        // 配置缓存写入
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_COMMENT, commentId)).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询
        when(delegate.findById(commentId)).thenReturn(Optional.of(dbComment));
        
        // When: 第一次查询
        Optional<Comment> firstResult = cachedRepository.findById(commentId);
        
        // Then: 验证所有字段与数据库一致
        assertTrue(firstResult.isPresent(), "第一次查询应该返回数据");
        Comment firstComment = firstResult.get();
        assertCommentEquals(dbComment, firstComment, "第一次查询结果应该与数据库一致");
        
        // 验证缓存中的所有字段与数据库一致
        assertNotNull(cachedData.get(), "数据应该被写入缓存");
        Comment cachedComment = (Comment) cachedData.get();
        assertCommentEquals(dbComment, cachedComment, "缓存数据应该与数据库一致");
        
        // When: 第二次查询
        Optional<Comment> secondResult = cachedRepository.findById(commentId);
        
        // Then: 验证所有字段与第一次查询一致
        assertTrue(secondResult.isPresent(), "第二次查询应该返回数据");
        Comment secondComment = secondResult.get();
        assertCommentEquals(firstComment, secondComment, "两次查询结果应该一致");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).findById(commentId);
    }

    // ==================== 辅助方法 ====================

    private Comment createTestComment(Long commentId, Long postId, Long authorId, String content) {
        return Comment.reconstitute(
                commentId,
                postId,
                authorId,
                content,
                null,  // imageIds
                null,  // voiceId
                null,  // voiceDuration
                null,  // parentId
                commentId,  // rootId (顶级评论)
                null,  // replyToUserId
                com.blog.comment.domain.model.CommentStatus.NORMAL,
                LocalDateTime.now(),
                LocalDateTime.now(),
                CommentStats.empty()
        );
    }
    
    private Comment createTestCommentWithStats(Long commentId, Long postId, Long authorId, 
                                               String content, CommentStats stats) {
        return Comment.reconstitute(
                commentId,
                postId,
                authorId,
                content,
                null,  // imageIds
                null,  // voiceId
                null,  // voiceDuration
                null,  // parentId
                commentId,  // rootId (顶级评论)
                null,  // replyToUserId
                com.blog.comment.domain.model.CommentStatus.NORMAL,
                LocalDateTime.now(),
                LocalDateTime.now(),
                stats
        );
    }

    private void assertCommentEquals(Comment expected, Comment actual, String message) {
        assertEquals(expected.getId(), actual.getId(), message + " - ID");
        assertEquals(expected.getPostId(), actual.getPostId(), message + " - 文章ID");
        assertEquals(expected.getAuthorId(), actual.getAuthorId(), message + " - 用户ID");
        assertEquals(expected.getContent(), actual.getContent(), message + " - 内容");
        assertEquals(expected.getStatus(), actual.getStatus(), message + " - 状态");
        
        // 验证统计信息
        if (expected.getStats() != null && actual.getStats() != null) {
            assertEquals(expected.getStats().getLikeCount(), actual.getStats().getLikeCount(), 
                    message + " - 点赞数");
            assertEquals(expected.getStats().getReplyCount(), actual.getStats().getReplyCount(), 
                    message + " - 回复数");
        }
    }
}
