package com.zhicore.comment.application.service;

import com.zhicore.comment.application.port.CommentCacheKeyResolver;
import com.zhicore.comment.application.port.store.CommentDetailCacheStore;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentDetailCacheServiceExceptionLockReleasePropertyTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentDetailCacheStore cacheStore;

    @Mock
    private LockManager lockManager;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    private CommentCacheKeyResolver commentCacheKeyResolver;
    private CommentDetailCacheService service;

    private void setUp() {
        MockitoAnnotations.openMocks(this);
        CacheProperties cacheProperties = new CacheProperties();
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

    @Property(tries = 50)
    void shouldUnlockWhenDatabaseFails(@ForAll @LongRange(min = 1L, max = 100000L) Long commentId) throws Exception {
        setUp();
        when(cacheStore.get(commentId)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", commentId)).thenReturn(true);
        String lockKey = commentCacheKeyResolver.lockDetail(commentId);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenThrow(new RuntimeException("db failed"));

        try {
            service.findById(commentId);
        } catch (Exception ignored) {
        }

        verify(commentRepository, atLeastOnce()).findById(commentId);
        verify(lockManager, times(1)).unlock(lockKey, false);
    }

    @Property(tries = 50)
    void shouldUnlockWhenCacheWriteFails(@ForAll @LongRange(min = 1L, max = 100000L) Long commentId) throws Exception {
        setUp();
        when(cacheStore.get(commentId)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", commentId)).thenReturn(true);
        String lockKey = commentCacheKeyResolver.lockDetail(commentId);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenReturn(java.util.Optional.empty());
        doThrow(new RuntimeException("cache write failed")).when(cacheStore).setNull(commentId, Duration.ofSeconds(60));

        service.findById(commentId);

        verify(lockManager, times(1)).unlock(lockKey, false);
    }

    @Property(tries = 50)
    void shouldNotUnlockWhenLockNotAcquired(@ForAll @LongRange(min = 1L, max = 100000L) Long commentId) throws Exception {
        setUp();
        when(cacheStore.get(commentId)).thenReturn(CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", commentId)).thenReturn(true);
        String lockKey = commentCacheKeyResolver.lockDetail(commentId);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(false);
        when(commentRepository.findById(commentId)).thenReturn(java.util.Optional.empty());

        service.findById(commentId);

        verify(lockManager, never()).unlock(lockKey, false);
    }
}
