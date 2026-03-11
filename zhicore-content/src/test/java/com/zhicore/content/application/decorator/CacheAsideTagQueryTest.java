package com.zhicore.content.application.decorator;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.application.port.cachekey.TagCacheKeyResolver;
import com.zhicore.content.application.port.store.TagQueryCacheStore;
import com.zhicore.content.application.query.TagQuery;
import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheAsideTagQuery 单元测试")
class CacheAsideTagQueryTest {

    @Mock private TagQuery delegate;
    @Mock private TagQueryCacheStore tagQueryCacheStore;
    @Mock private LockManager lockManager;
    @Mock private TagCacheKeyResolver tagCacheKeyResolver;

    private CacheAsideTagQuery query;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.getTtl().setEntityDetail(1800);
        cacheProperties.getTtl().setList(600);
        cacheProperties.getTtl().setStats(3600);
        cacheProperties.getTtl().setNullValue(60);
        cacheProperties.getLock().setWaitTime(1);
        cacheProperties.getLock().setLeaseTime(3);
        cacheProperties.getJitter().setMaxSeconds(5);

        query = new CacheAsideTagQuery(
                delegate,
                tagQueryCacheStore,
                lockManager,
                cacheProperties,
                tagCacheKeyResolver
        );
    }

    @Test
    @DisplayName("标签详情缓存命中时直接返回")
    void shouldReturnCachedDetailDirectly() {
        TagDetailView cachedView = TagDetailView.builder()
                .id(101L)
                .name("Java")
                .slug("java")
                .build();
        when(tagQueryCacheStore.getDetail(101L)).thenReturn(CacheResult.hit(cachedView));

        TagDetailView result = query.getDetail(101L);

        assertEquals("Java", result.getName());
        verify(delegate, never()).getDetail(101L);
        verify(lockManager, never()).tryLock(any(), any(), any());
    }

    @Test
    @DisplayName("热门标签缓存未命中时应回源并回填")
    void shouldLoadHotTagsFromSourceAndBackfill() {
        List<HotTagView> hotTags = List.of(
                HotTagView.builder().id(1L).name("Spring").slug("spring").postCount(12L).build()
        );
        when(tagCacheKeyResolver.lockHotTags(10)).thenReturn("lock:tag:hot:10");
        when(tagQueryCacheStore.getHotTags(10)).thenReturn(CacheResult.miss(), CacheResult.miss());
        when(lockManager.tryLock(eq("lock:tag:hot:10"), any(), any())).thenReturn(true);
        when(delegate.getHotTags(10)).thenReturn(hotTags);

        List<HotTagView> result = query.getHotTags(10);

        assertEquals(1, result.size());
        verify(tagQueryCacheStore).setHotTags(eq(10), eq(hotTags), any());
        verify(lockManager).unlock("lock:tag:hot:10");
    }
}
