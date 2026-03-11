package com.zhicore.content.application.decorator;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.application.port.cachekey.PostCacheKeyResolver;
import com.zhicore.content.application.port.store.PostQueryCacheStore;
import com.zhicore.content.application.query.PostQuery;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheAsidePostQuery 单元测试")
class CacheAsidePostQueryTest {

    private static final PostId POST_ID = PostId.of(1001L);

    @Mock private PostQuery delegate;
    @Mock private PostQueryCacheStore postQueryCacheStore;
    @Mock private LockManager lockManager;
    @Mock private PostCacheKeyResolver postCacheKeyResolver;

    private CacheAsidePostQuery query;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.getTtl().setEntityDetail(1800);
        cacheProperties.getTtl().setList(600);
        cacheProperties.getTtl().setNullValue(60);
        cacheProperties.getLock().setWaitTime(1);
        cacheProperties.getLock().setLeaseTime(3);
        cacheProperties.getJitter().setMaxSeconds(5);

        query = new CacheAsidePostQuery(
                delegate,
                postQueryCacheStore,
                lockManager,
                cacheProperties,
                postCacheKeyResolver
        );
    }

    @Test
    @DisplayName("详情缓存命中时直接返回")
    void shouldReturnCachedDetailDirectly() {
        PostDetailView cachedView = PostDetailView.builder()
                .id(POST_ID)
                .title("cached")
                .status(PostStatus.PUBLISHED)
                .build();
        when(postQueryCacheStore.getDetail(POST_ID)).thenReturn(CacheResult.hit(cachedView));

        PostDetailView result = query.getDetail(POST_ID);

        assertEquals("cached", result.getTitle());
        verify(delegate, never()).getDetail(POST_ID);
        verify(lockManager, never()).tryLock(any(), any(), any());
    }

    @Test
    @DisplayName("详情缓存未命中时应回源并回填")
    void shouldLoadFromSourceAndBackfillDetail() {
        PostDetailView sourceView = PostDetailView.builder()
                .id(POST_ID)
                .title("source")
                .status(PostStatus.PUBLISHED)
                .build();
        when(postCacheKeyResolver.lockDetail(POST_ID)).thenReturn("lock:post:detail:1001");
        when(postQueryCacheStore.getDetail(POST_ID)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(lockManager.tryLock(eq("lock:post:detail:1001"), any(), any())).thenReturn(true);
        when(delegate.getDetail(POST_ID)).thenReturn(sourceView);

        PostDetailView result = query.getDetail(POST_ID);

        assertEquals("source", result.getTitle());
        verify(postQueryCacheStore).setDetail(eq(POST_ID), eq(sourceView), any());
        verify(lockManager).unlock("lock:post:detail:1001");
    }
}
