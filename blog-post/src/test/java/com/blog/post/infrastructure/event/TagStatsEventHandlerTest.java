package com.blog.post.infrastructure.event;

import com.blog.api.event.post.PostDeletedEvent;
import com.blog.post.domain.event.PostCreatedEvent;
import com.blog.post.domain.event.PostTagsUpdatedEvent;
import com.blog.post.infrastructure.cache.TagRedisKeys;
import com.blog.post.infrastructure.repository.mapper.TagStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TagStatsEventHandler 单元测试
 * 
 * 测试统计更新逻辑
 * Requirements: 4.4
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TagStatsEventHandler 单元测试")
class TagStatsEventHandlerTest {

    @Mock
    private TagStatsMapper tagStatsMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private TagStatsEventHandler eventHandler;

    private PostCreatedEvent postCreatedEvent;
    private PostDeletedEvent postDeletedEvent;
    private PostTagsUpdatedEvent postTagsUpdatedEvent;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        postCreatedEvent = new PostCreatedEvent(
            "1",
            "Test Post",
            "Test Content",
            "Test Excerpt",
            "100",
            "Test Author",
            Arrays.asList("1001", "1002"),
            "10",
            "Technology",
            "PUBLISHED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        postDeletedEvent = new PostDeletedEvent(1L, 100L);

        postTagsUpdatedEvent = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList("1001"),
            Arrays.asList("1001", "1002", "1003"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    // ========================================
    // PostCreatedEvent Tests
    // ========================================

    @Test
    @DisplayName("处理文章创建事件 - 成功更新标签统计")
    void testHandlePostCreated_Success() {
        // Given
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostCreated(postCreatedEvent);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        assertEquals(2, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));

        // 验证缓存失效
        verify(redisTemplate, times(2)).delete(anyString());
        verify(redisTemplate).keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
    }

    @Test
    @DisplayName("处理文章创建事件 - 无标签")
    void testHandlePostCreated_NoTags() {
        // Given
        PostCreatedEvent eventWithoutTags = new PostCreatedEvent(
            "1",
            "Test Post",
            "Test Content",
            "Test Excerpt",
            "100",
            "Test Author",
            null,
            "10",
            "Technology",
            "PUBLISHED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // When
        eventHandler.handlePostCreated(eventWithoutTags);

        // Then
        verify(tagStatsMapper, never()).batchUpsertTagStats(anyList());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("处理文章创建事件 - 空标签列表")
    void testHandlePostCreated_EmptyTagList() {
        // Given
        PostCreatedEvent eventWithEmptyTags = new PostCreatedEvent(
            "1",
            "Test Post",
            "Test Content",
            "Test Excerpt",
            "100",
            "Test Author",
            Arrays.asList(),
            "10",
            "Technology",
            "PUBLISHED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // When
        eventHandler.handlePostCreated(eventWithEmptyTags);

        // Then
        verify(tagStatsMapper, never()).batchUpsertTagStats(anyList());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("处理文章创建事件 - 数据库更新失败不影响主流程")
    void testHandlePostCreated_DatabaseFailure() {
        // Given
        doThrow(new RuntimeException("Database error")).when(tagStatsMapper).batchUpsertTagStats(anyList());

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostCreated(postCreatedEvent));
    }

    @Test
    @DisplayName("处理文章创建事件 - 缓存失效失败不影响主流程")
    void testHandlePostCreated_CacheInvalidationFailure() {
        // Given
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        doThrow(new RuntimeException("Redis error")).when(redisTemplate).delete(anyString());

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostCreated(postCreatedEvent));
        
        // 验证数据库更新仍然执行
        verify(tagStatsMapper).batchUpsertTagStats(anyList());
    }

    @Test
    @DisplayName("处理文章创建事件 - 无效标签ID被过滤")
    void testHandlePostCreated_InvalidTagIds() {
        // Given
        PostCreatedEvent eventWithInvalidIds = new PostCreatedEvent(
            "1",
            "Test Post",
            "Test Content",
            "Test Excerpt",
            "100",
            "Test Author",
            Arrays.asList("1001", "invalid", "1002", ""),
            "10",
            "Technology",
            "PUBLISHED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostCreated(eventWithInvalidIds);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        assertEquals(2, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));
    }

    // ========================================
    // PostDeletedEvent Tests
    // ========================================

    @Test
    @DisplayName("处理文章删除事件 - 成功清除热门标签缓存")
    void testHandlePostDeleted_Success() {
        // Given
        Set<String> hotTagsKeys = new HashSet<>(Arrays.asList(
            TagRedisKeys.HOT_TAGS_PREFIX + "10",
            TagRedisKeys.HOT_TAGS_PREFIX + "20"
        ));
        when(redisTemplate.keys(TagRedisKeys.HOT_TAGS_PREFIX + "*")).thenReturn(hotTagsKeys);
        when(redisTemplate.delete(hotTagsKeys)).thenReturn(2L);

        // When
        eventHandler.handlePostDeleted(postDeletedEvent);

        // Then
        verify(redisTemplate).keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
        verify(redisTemplate).delete(hotTagsKeys);
    }

    @Test
    @DisplayName("处理文章删除事件 - 无热门标签缓存")
    void testHandlePostDeleted_NoHotTagsCache() {
        // Given
        when(redisTemplate.keys(TagRedisKeys.HOT_TAGS_PREFIX + "*")).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostDeleted(postDeletedEvent);

        // Then
        verify(redisTemplate).keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
        verify(redisTemplate, never()).delete(any(Set.class));
    }

    @Test
    @DisplayName("处理文章删除事件 - 缓存失效失败不影响主流程")
    void testHandlePostDeleted_CacheFailure() {
        // Given
        doThrow(new RuntimeException("Redis error")).when(redisTemplate).keys(anyString());

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostDeleted(postDeletedEvent));
    }

    // ========================================
    // PostTagsUpdatedEvent Tests
    // ========================================

    @Test
    @DisplayName("处理文章标签更新事件 - 成功更新统计")
    void testHandlePostTagsUpdated_Success() {
        // Given
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostTagsUpdated(postTagsUpdatedEvent);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        // 应该包含旧标签和新标签的并集：1001, 1002, 1003
        assertEquals(3, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));
        assertTrue(capturedTagIds.contains(1003L));

        // 验证缓存失效
        verify(redisTemplate, times(3)).delete(anyString());
        verify(redisTemplate).keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 仅有旧标签")
    void testHandlePostTagsUpdated_OnlyOldTags() {
        // Given
        PostTagsUpdatedEvent eventWithOnlyOldTags = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList("1001", "1002"),
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostTagsUpdated(eventWithOnlyOldTags);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        assertEquals(2, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 仅有新标签")
    void testHandlePostTagsUpdated_OnlyNewTags() {
        // Given
        PostTagsUpdatedEvent eventWithOnlyNewTags = new PostTagsUpdatedEvent(
            "1",
            null,
            Arrays.asList("1001", "1002"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostTagsUpdated(eventWithOnlyNewTags);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        assertEquals(2, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 无标签变更")
    void testHandlePostTagsUpdated_NoTags() {
        // Given
        PostTagsUpdatedEvent eventWithoutTags = new PostTagsUpdatedEvent(
            "1",
            null,
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // When
        eventHandler.handlePostTagsUpdated(eventWithoutTags);

        // Then
        verify(tagStatsMapper, never()).batchUpsertTagStats(anyList());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 空标签列表")
    void testHandlePostTagsUpdated_EmptyTagLists() {
        // Given
        PostTagsUpdatedEvent eventWithEmptyTags = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList(),
            Arrays.asList(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // When
        eventHandler.handlePostTagsUpdated(eventWithEmptyTags);

        // Then
        verify(tagStatsMapper, never()).batchUpsertTagStats(anyList());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 标签去重")
    void testHandlePostTagsUpdated_DuplicateTags() {
        // Given
        PostTagsUpdatedEvent eventWithDuplicates = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList("1001", "1002"),
            Arrays.asList("1002", "1003"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostTagsUpdated(eventWithDuplicates);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        // 应该去重：1001, 1002, 1003
        assertEquals(3, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));
        assertTrue(capturedTagIds.contains(1003L));
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 数据库更新失败不影响主流程")
    void testHandlePostTagsUpdated_DatabaseFailure() {
        // Given
        doThrow(new RuntimeException("Database error")).when(tagStatsMapper).batchUpsertTagStats(anyList());

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostTagsUpdated(postTagsUpdatedEvent));
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 缓存失效失败不影响主流程")
    void testHandlePostTagsUpdated_CacheInvalidationFailure() {
        // Given
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        doThrow(new RuntimeException("Redis error")).when(redisTemplate).delete(anyString());

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostTagsUpdated(postTagsUpdatedEvent));
        
        // 验证数据库更新仍然执行
        verify(tagStatsMapper).batchUpsertTagStats(anyList());
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 无效标签ID被过滤")
    void testHandlePostTagsUpdated_InvalidTagIds() {
        // Given
        PostTagsUpdatedEvent eventWithInvalidIds = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList("1001", "invalid"),
            Arrays.asList("1002", "", "1003"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        eventHandler.handlePostTagsUpdated(eventWithInvalidIds);

        // Then
        ArgumentCaptor<List<Long>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(tagIdsCaptor.capture());

        List<Long> capturedTagIds = tagIdsCaptor.getValue();
        assertEquals(3, capturedTagIds.size());
        assertTrue(capturedTagIds.contains(1001L));
        assertTrue(capturedTagIds.contains(1002L));
        assertTrue(capturedTagIds.contains(1003L));
    }

    // ========================================
    // Cache Invalidation Tests
    // ========================================

    @Test
    @DisplayName("缓存失效 - 验证正确的缓存键")
    void testCacheInvalidation_CorrectKeys() {
        // Given
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        Set<String> hotTagsKeys = new HashSet<>(Arrays.asList(
            TagRedisKeys.HOT_TAGS_PREFIX + "10"
        ));
        when(redisTemplate.keys(TagRedisKeys.HOT_TAGS_PREFIX + "*")).thenReturn(hotTagsKeys);
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.delete(any(Set.class))).thenReturn(1L);

        // When
        eventHandler.handlePostCreated(postCreatedEvent);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(2)).delete(keyCaptor.capture());

        List<String> capturedKeys = keyCaptor.getAllValues();
        assertTrue(capturedKeys.contains(TagRedisKeys.tagStats(1001L)));
        assertTrue(capturedKeys.contains(TagRedisKeys.tagStats(1002L)));
        
        verify(redisTemplate).keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
        verify(redisTemplate).delete(hotTagsKeys);
    }

    @Test
    @DisplayName("缓存失效 - 热门标签缓存为null")
    void testCacheInvalidation_HotTagsCacheNull() {
        // Given
        doNothing().when(tagStatsMapper).batchUpsertTagStats(anyList());
        when(redisTemplate.keys(TagRedisKeys.HOT_TAGS_PREFIX + "*")).thenReturn(null);

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostCreated(postCreatedEvent));
        
        verify(redisTemplate).keys(TagRedisKeys.HOT_TAGS_PREFIX + "*");
        verify(redisTemplate, never()).delete(any(Set.class));
    }
}
