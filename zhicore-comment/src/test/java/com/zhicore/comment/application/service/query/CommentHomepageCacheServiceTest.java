package com.zhicore.comment.application.service.query;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.port.CommentCacheKeyResolver;
import com.zhicore.comment.application.port.store.CommentHomepageCacheStore;
import com.zhicore.comment.application.port.store.RankingHotPostCandidateStore;
import com.zhicore.comment.infrastructure.config.CommentHomepageCacheProperties;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentHomepageCacheService Tests")
class CommentHomepageCacheServiceTest {

    @Mock
    private CommentHomepageCacheStore commentHomepageCacheStore;

    @Mock
    private RankingHotPostCandidateStore rankingHotPostCandidateStore;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    @Mock
    private LockManager lockManager;

    @Mock
    private CommentCacheKeyResolver commentCacheKeyResolver;

    private CommentHomepageCacheService service;

    @BeforeEach
    void setUp() {
        CommentHomepageCacheProperties properties = new CommentHomepageCacheProperties();
        properties.setTtlSeconds(30);
        properties.setTtlJitterSeconds(3);
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.getLock().setWaitTime(5);
        cacheProperties.getLock().setLeaseTime(10);
        service = new CommentHomepageCacheService(
                commentHomepageCacheStore,
                rankingHotPostCandidateStore,
                hotDataIdentifier,
                properties,
                lockManager,
                commentCacheKeyResolver,
                cacheProperties
        );
    }

    @Test
    @DisplayName("首页命中 ranking 候选集且快照存在时应直接返回缓存")
    void shouldReturnCachedSnapshotWhenRankingCandidateMatched() {
        Long postId = 1001L;
        PageResult<CommentVO> cached = PageResult.of(0, 20, 1, List.of(CommentVO.builder().id(1L).build()));
        AtomicInteger loadCount = new AtomicInteger();

        when(rankingHotPostCandidateStore.contains(postId)).thenReturn(true);
        when(commentHomepageCacheStore.get(postId, CommentSortType.HOT, 20, 5)).thenReturn(Optional.of(cached));

        PageResult<CommentVO> result = service.getTopLevelCommentsPage(
                postId,
                0,
                20,
                CommentSortType.HOT,
                5,
                () -> {
                    loadCount.incrementAndGet();
                    return PageResult.of(0, 20, 0, List.of());
                }
        );

        assertThat(result).isSameAs(cached);
        assertThat(loadCount).hasValue(0);
        verify(hotDataIdentifier).recordAccess("post", postId);
        verify(commentHomepageCacheStore, never()).set(eq(postId), eq(CommentSortType.HOT), eq(20), eq(5), any(), any());
    }

    @Test
    @DisplayName("首页缓存 miss 且本地访问热度命中时应回源并回填快照")
    void shouldLoadAndBackfillWhenLocallyHot() {
        Long postId = 1002L;
        PageResult<CommentVO> loaded = PageResult.of(0, 10, 2, List.of(CommentVO.builder().id(2L).build()));

        when(rankingHotPostCandidateStore.contains(postId)).thenReturn(false);
        when(hotDataIdentifier.isHotData("post", postId)).thenReturn(true);
        when(commentHomepageCacheStore.get(postId, CommentSortType.TIME, 10, 6))
                .thenReturn(Optional.empty(), Optional.empty());
        when(commentCacheKeyResolver.lockHomepageSnapshot(postId, CommentSortType.TIME, 10, 6))
                .thenReturn("comment:lock:homepage:1002:time:10:6");
        when(lockManager.tryLock(eq("comment:lock:homepage:1002:time:10:6"), any(Duration.class), any(Duration.class), eq(false)))
                .thenReturn(true);
        when(lockManager.isHeldByCurrentThread("comment:lock:homepage:1002:time:10:6", false)).thenReturn(true);

        PageResult<CommentVO> result = service.getTopLevelCommentsPage(
                postId,
                0,
                10,
                CommentSortType.TIME,
                6,
                () -> loaded
        );

        assertThat(result).isSameAs(loaded);
        verify(commentHomepageCacheStore).set(
                eq(postId),
                eq(CommentSortType.TIME),
                eq(10),
                eq(6),
                eq(loaded),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("首页缓存 miss 且获取到锁后若二次检查命中则不应回源")
    void shouldAvoidReloadWhenSnapshotAppearsAfterLockAcquired() {
        Long postId = 1005L;
        PageResult<CommentVO> cached = PageResult.of(0, 20, 1, List.of(CommentVO.builder().id(5L).build()));
        AtomicInteger loadCount = new AtomicInteger();

        when(rankingHotPostCandidateStore.contains(postId)).thenReturn(true);
        when(commentHomepageCacheStore.get(postId, CommentSortType.HOT, 20, 3))
                .thenReturn(Optional.empty(), Optional.of(cached));
        when(commentCacheKeyResolver.lockHomepageSnapshot(postId, CommentSortType.HOT, 20, 3))
                .thenReturn("comment:lock:homepage:1005:hot:20:3");
        when(lockManager.tryLock(eq("comment:lock:homepage:1005:hot:20:3"), any(Duration.class), any(Duration.class), eq(false)))
                .thenReturn(true);
        when(lockManager.isHeldByCurrentThread("comment:lock:homepage:1005:hot:20:3", false)).thenReturn(true);

        PageResult<CommentVO> result = service.getTopLevelCommentsPage(
                postId,
                0,
                20,
                CommentSortType.HOT,
                3,
                () -> {
                    loadCount.incrementAndGet();
                    return PageResult.of(0, 20, 0, List.of());
                }
        );

        assertThat(result).isSameAs(cached);
        assertThat(loadCount).hasValue(0);
        verify(commentHomepageCacheStore, never()).set(anyLong(), any(), anyInt(), anyInt(), any(), any());
        verify(lockManager).unlock("comment:lock:homepage:1005:hot:20:3", false);
    }

    @Test
    @DisplayName("首页缓存 miss 且未获取到锁时应优先读取同伴已回填的快照")
    void shouldUseBackfilledSnapshotWhenLockNotAcquired() {
        Long postId = 1006L;
        PageResult<CommentVO> cached = PageResult.of(0, 20, 1, List.of(CommentVO.builder().id(6L).build()));
        AtomicInteger loadCount = new AtomicInteger();

        when(rankingHotPostCandidateStore.contains(postId)).thenReturn(true);
        when(commentHomepageCacheStore.get(postId, CommentSortType.HOT, 20, 3))
                .thenReturn(Optional.empty(), Optional.of(cached));
        when(commentCacheKeyResolver.lockHomepageSnapshot(postId, CommentSortType.HOT, 20, 3))
                .thenReturn("comment:lock:homepage:1006:hot:20:3");
        when(lockManager.tryLock(eq("comment:lock:homepage:1006:hot:20:3"), any(Duration.class), any(Duration.class), eq(false)))
                .thenReturn(false);

        PageResult<CommentVO> result = service.getTopLevelCommentsPage(
                postId,
                0,
                20,
                CommentSortType.HOT,
                3,
                () -> {
                    loadCount.incrementAndGet();
                    return PageResult.of(0, 20, 0, List.of());
                }
        );

        assertThat(result).isSameAs(cached);
        assertThat(loadCount).hasValue(0);
        verify(commentHomepageCacheStore, never()).set(anyLong(), any(), anyInt(), anyInt(), any(), any());
        verify(lockManager, never()).unlock(anyString(), eq(false));
    }

    @Test
    @DisplayName("首页未命中任何启用条件时应直接回源且不读写缓存")
    void shouldBypassCacheWhenPostIsNotHot() {
        Long postId = 1003L;
        PageResult<CommentVO> loaded = PageResult.of(0, 20, 0, List.of());

        when(rankingHotPostCandidateStore.contains(postId)).thenReturn(false);
        when(hotDataIdentifier.isHotData("post", postId)).thenReturn(false);

        PageResult<CommentVO> result = service.getTopLevelCommentsPage(
                postId,
                0,
                20,
                CommentSortType.TIME,
                3,
                () -> loaded
        );

        assertThat(result).isSameAs(loaded);
        verify(commentHomepageCacheStore, never()).get(anyLong(), any(), anyInt(), anyInt());
        verify(commentHomepageCacheStore, never()).set(anyLong(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("非首页分页请求不应使用首页缓存")
    void shouldBypassCacheForNonHomepageRequest() {
        Long postId = 1004L;
        PageResult<CommentVO> loaded = PageResult.of(1, 20, 40, List.of());

        PageResult<CommentVO> result = service.getTopLevelCommentsPage(
                postId,
                1,
                20,
                CommentSortType.HOT,
                3,
                () -> loaded
        );

        assertThat(result).isSameAs(loaded);
        verify(hotDataIdentifier).recordAccess("post", postId);
        verify(rankingHotPostCandidateStore, never()).contains(anyLong());
        verify(commentHomepageCacheStore, never()).get(anyLong(), any(), anyInt(), anyInt());
    }
}
