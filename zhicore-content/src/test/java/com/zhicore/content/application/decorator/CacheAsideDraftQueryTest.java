package com.zhicore.content.application.decorator;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.application.port.store.DraftCacheStore;
import com.zhicore.content.domain.service.DraftQueryService;
import com.zhicore.content.domain.valueobject.DraftSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheAsideDraftQuery 单元测试")
class CacheAsideDraftQueryTest {

    @Mock private DraftCacheStore draftCacheStore;
    @Mock private DraftQueryService delegate;

    private CacheAsideDraftQuery service;

    private static final Long POST_ID = 1001L;
    private static final Long USER_ID = 2001L;

    @BeforeEach
    void setUp() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.getDraft().setTtl(300);
        cacheProperties.getDraft().setListTtl(180);
        cacheProperties.getJitter().setMaxSeconds(5);

        service = new CacheAsideDraftQuery(draftCacheStore, cacheProperties, delegate);
    }

    private DraftSnapshot createDraft() {
        return DraftSnapshot.builder()
                .id("draft-1")
                .postId(String.valueOf(POST_ID))
                .userId(String.valueOf(USER_ID))
                .content("draft content")
                .build();
    }

    @Nested
    @DisplayName("getLatestDraft")
    class GetLatestDraftTests {

        @Test
        @DisplayName("缓存命中时直接返回")
        void shouldReturnCachedDraft() {
            DraftSnapshot snapshot = createDraft();
            when(draftCacheStore.getLatestDraft(POST_ID, USER_ID)).thenReturn(CacheResult.hit(snapshot));

            Optional<DraftSnapshot> result = service.getLatestDraft(POST_ID, USER_ID);

            assertTrue(result.isPresent());
            assertEquals("draft content", result.get().getContent());
            verify(delegate, never()).getLatestDraft(POST_ID, USER_ID);
        }

        @Test
        @DisplayName("空值缓存命中时返回 empty")
        void shouldReturnEmptyWhenNullMarkerHit() {
            when(draftCacheStore.getLatestDraft(POST_ID, USER_ID)).thenReturn(CacheResult.nullValue());

            Optional<DraftSnapshot> result = service.getLatestDraft(POST_ID, USER_ID);

            assertTrue(result.isEmpty());
            verify(delegate, never()).getLatestDraft(POST_ID, USER_ID);
        }

        @Test
        @DisplayName("缓存未命中时回源并回填")
        void shouldQueryDelegateAndBackfillWhenCacheMiss() {
            DraftSnapshot snapshot = createDraft();
            when(draftCacheStore.getLatestDraft(POST_ID, USER_ID)).thenReturn(CacheResult.miss());
            when(delegate.getLatestDraft(POST_ID, USER_ID)).thenReturn(Optional.of(snapshot));

            Optional<DraftSnapshot> result = service.getLatestDraft(POST_ID, USER_ID);

            assertTrue(result.isPresent());
            verify(draftCacheStore).setLatestDraft(
                    org.mockito.ArgumentMatchers.eq(POST_ID),
                    org.mockito.ArgumentMatchers.eq(USER_ID),
                    org.mockito.ArgumentMatchers.eq(snapshot),
                    org.mockito.ArgumentMatchers.any()
            );
        }
    }

    @Nested
    @DisplayName("getUserDrafts")
    class GetUserDraftsTests {

        @Test
        @DisplayName("缓存未命中时应查询并回填列表")
        void shouldQueryDelegateAndBackfillListWhenCacheMiss() {
            DraftSnapshot snapshot = createDraft();
            when(draftCacheStore.getUserDrafts(USER_ID)).thenReturn(CacheResult.miss());
            when(delegate.getUserDrafts(USER_ID)).thenReturn(List.of(snapshot));

            List<DraftSnapshot> result = service.getUserDrafts(USER_ID);

            assertEquals(1, result.size());
            verify(draftCacheStore).setUserDrafts(
                    org.mockito.ArgumentMatchers.eq(USER_ID),
                    org.mockito.ArgumentMatchers.eq(List.of(snapshot)),
                    org.mockito.ArgumentMatchers.any()
            );
        }

        @Test
        @DisplayName("空列表缓存命中时应直接返回空")
        void shouldReturnEmptyListWhenNullMarkerHit() {
            when(draftCacheStore.getUserDrafts(USER_ID)).thenReturn(CacheResult.nullValue());

            List<DraftSnapshot> result = service.getUserDrafts(USER_ID);

            assertTrue(result.isEmpty());
            verify(delegate, never()).getUserDrafts(USER_ID);
        }
    }

    @Test
    @DisplayName("warmUpUserDraftsCache 应查询并写入列表缓存")
    void shouldWarmUpUserDraftsCache() {
        DraftSnapshot snapshot = createDraft();
        when(delegate.getUserDrafts(USER_ID)).thenReturn(List.of(snapshot));

        service.warmUpUserDraftsCache(USER_ID);

        verify(draftCacheStore).setUserDrafts(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(List.of(snapshot)),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("warmUpDraftCache 无数据时不写缓存")
    void shouldSkipWarmUpWhenNoDraft() {
        when(delegate.getLatestDraft(POST_ID, USER_ID)).thenReturn(Optional.empty());

        service.warmUpDraftCache(POST_ID, USER_ID);

        verify(draftCacheStore, never()).setLatestDraft(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(draftCacheStore, never()).setLatestDraftNull(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
