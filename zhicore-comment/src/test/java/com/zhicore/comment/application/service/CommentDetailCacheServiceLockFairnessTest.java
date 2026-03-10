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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentDetailCacheService 锁公平性测试")
class CommentDetailCacheServiceLockFairnessTest {

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
    @DisplayName("配置公平锁时应以 fair=true 获取锁")
    void findById_shouldUseFairLockWhenConfigured() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        cacheProperties.getLock().setFair(true);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), true)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, true)).thenReturn(true);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(lockManager).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), true);
        verify(lockManager).unlock(lockKey, true);
        verify(lockManager, never()).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false);
    }

    @Test
    @DisplayName("配置非公平锁时应以 fair=false 获取锁")
    void findById_shouldUseNonFairLockWhenConfigured() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        cacheProperties.getLock().setFair(false);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(lockManager).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false);
        verify(lockManager).unlock(lockKey, false);
        verify(lockManager, never()).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), true);
    }

    @Test
    @DisplayName("锁等待队列长度监控当前仍返回 -1")
    void getLockQueueLength_shouldReturnMinusOne() {
        assertEquals(-1, service.getLockQueueLength("comment:lock:1"));
    }

    private Comment createComment(Long id) {
        return Comment.createTopLevel(id, 100L, 200L, "test comment", null, null, null);
    }
}
