package com.zhicore.content.application.decorator;

import com.zhicore.content.application.port.store.DraftCacheStore;
import com.zhicore.content.domain.service.DraftCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvalidateDraftCacheCommandService 单元测试")
class InvalidateDraftCacheCommandServiceTest {

    @Mock
    private DraftCommandService delegate;

    @Mock
    private DraftCacheStore draftCacheStore;

    @Test
    @DisplayName("保存草稿后应失效缓存")
    void shouldEvictCacheAfterSave() {
        InvalidateDraftCacheCommandService service =
                new InvalidateDraftCacheCommandService(delegate, draftCacheStore);

        service.saveDraft(1001L, 2001L, "content", true);

        verify(delegate).saveDraft(1001L, 2001L, "content", true);
        verify(draftCacheStore).evictDraft(1001L, 2001L);
    }

    @Test
    @DisplayName("删除草稿后应失效缓存")
    void shouldEvictCacheAfterDelete() {
        InvalidateDraftCacheCommandService service =
                new InvalidateDraftCacheCommandService(delegate, draftCacheStore);

        service.deleteDraft(1001L, 2001L);

        verify(delegate).deleteDraft(1001L, 2001L);
        verify(draftCacheStore).evictDraft(1001L, 2001L);
    }

    @Test
    @DisplayName("缓存删除失败不应影响保存流程")
    void shouldIgnoreCacheEvictionFailureAfterSave() {
        InvalidateDraftCacheCommandService service =
                new InvalidateDraftCacheCommandService(delegate, draftCacheStore);
        doThrow(new RuntimeException("redis down")).when(draftCacheStore).evictDraft(1001L, 2001L);

        service.saveDraft(1001L, 2001L, "content", false);

        verify(delegate).saveDraft(1001L, 2001L, "content", false);
    }
}
