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
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentDetailCacheService 异常处理测试")
class CommentDetailCacheServiceExceptionTest {

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

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("缓存读取异常时应直接降级数据库")
    void findById_shouldFallbackToDbWhenCacheReadFails() {
        Comment comment = createComment(1L);
        when(cacheStore.get(1L)).thenThrow(new RedisConnectionFailureException("redis down"));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(commentRepository).findById(1L);
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("锁实现异常时应降级数据库")
    void findById_shouldFallbackToDbWhenLockSystemFails() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false))
                .thenThrow(new RedisException("lock redis error"));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        verify(commentRepository).findById(1L);
    }

    @Test
    @DisplayName("锁释放失败不应影响结果返回")
    void findById_shouldIgnoreUnlockFailure() throws Exception {
        Comment comment = createComment(1L);
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        org.mockito.Mockito.doThrow(new RuntimeException("unlock failed")).when(lockManager).unlock(lockKey, false);

        Optional<Comment> result = service.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        verify(lockManager).unlock(lockKey, false);
    }

    @Test
    @DisplayName("加锁后数据库异常应继续上抛并释放锁")
    void findById_shouldRethrowDbExceptionAfterLock() throws Exception {
        String lockKey = commentCacheKeyResolver.lockDetail(1L);
        when(cacheStore.get(1L)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(hotDataIdentifier.isHotData("comment", 1L)).thenReturn(true);
        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10), false)).thenReturn(true);
        when(lockManager.isHeldByCurrentThread(lockKey, false)).thenReturn(true);
        when(commentRepository.findById(1L)).thenThrow(new IllegalStateException("db failed"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.findById(1L));

        assertEquals("db failed", exception.getMessage());
        verify(lockManager).unlock(lockKey, false);
    }

    private Comment createComment(Long id) {
        return Comment.createTopLevel(id, 100L, 200L, "test comment", null, null, null);
    }
}
