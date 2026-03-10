package com.zhicore.comment.application.service;

import com.zhicore.comment.application.port.CommentCacheKeyResolver;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentDetailCacheService 缓存测试")
class CommentDetailCacheServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentDetailCacheStore cacheStore;

    @Mock
    private LockManager lockManager;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    private CommentCacheKeyResolver commentCacheKeyResolver;
    private CacheProperties cacheProperties;
    private CommentDetailCacheService service;

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        cacheProperties.getLock().setWaitTime(5);
        cacheProperties.getLock().setLeaseTime(10);
        cacheProperties.getTtl().setEntityDetail(600);
        cacheProperties.getTtl().setNullValue(60);
        commentCacheKeyResolver = new com.zhicore.comment.infrastructure.cache.DefaultCommentCacheKeyResolver();

        service = new CommentDetailCacheService(
                commentRepository,
                cacheStore,
                lockManager,
                cacheProperties,
                hotDataIdentifier,
                commentCacheKeyResolver
        );
    }

    @Test
    @DisplayName("缓存命中时应直接返回")
    void findById_shouldReturnCachedComment() {
        Comment comment = createComment(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.hit(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        verify(commentRepository, never()).findById(any());
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("空值缓存命中时应返回 empty")
    void findById_shouldReturnEmptyWhenNullMarkerHit() {
        when(cacheStore.get(1L)).thenReturn(CacheResult.nullValue());

        Optional<Comment> result = service.findById(1L);

        assertFalse(result.isPresent());
        verify(commentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("非热点缓存未命中时应查询数据库并回填缓存")
    void findById_shouldLoadFromDbWhenMissAndNotHot() {
        Comment comment = createComment(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(hotDataIdentifier).recordAccess("comment", 1L);
        verify(commentRepository).findById(1L);
        verify(cacheStore).set(eq(1L), eq(comment), any(Duration.class));
    }

    @Test
    @DisplayName("热点缓存未命中时应加锁后查询数据库")
    void findById_shouldUseLockForHotComment() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(lockManager).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false);
        verify(commentRepository).findById(1L);
        verify(cacheStore).set(eq(1L), eq(comment), any(Duration.class));
        verify(lockManager).unlock(lockKey, false);
    }

    @Test
    @DisplayName("热点数据加锁后若其他线程已回填缓存应直接返回")
    void findById_shouldUseDoubleCheckAfterLock() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss(), CacheResult.hit(comment));
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(commentRepository, never()).findById(any());
        verify(lockManager).unlock(lockKey, false);
    }

    @Test
    @DisplayName("热点数据获取锁失败时应降级查询数据库")
    void findById_shouldFallbackToDbWhenLockNotAcquired() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(commentRepository).findById(1L);
        verify(lockManager, never()).unlock(any(), any(Boolean.class));
    }

    private Comment createComment(Long id) {
        return Comment.createTopLevel(id, 100L, 200L, "test comment", null, null, null);
    }
}
