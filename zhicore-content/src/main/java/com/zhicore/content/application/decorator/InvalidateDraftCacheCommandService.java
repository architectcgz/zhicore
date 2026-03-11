package com.zhicore.content.application.decorator;

import com.zhicore.content.application.port.store.DraftCacheStore;
import com.zhicore.content.domain.service.DraftCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 草稿写路径缓存失效装饰器。
 *
 * 在草稿保存和删除成功后清理相关缓存，保持读写分离下的缓存一致性。
 */
@Slf4j
@Primary
@Service
public class InvalidateDraftCacheCommandService implements DraftCommandService {

    private final DraftCommandService delegate;
    private final DraftCacheStore draftCacheStore;

    public InvalidateDraftCacheCommandService(
            @Qualifier("draftServiceImpl") DraftCommandService delegate,
            DraftCacheStore draftCacheStore) {
        this.delegate = delegate;
        this.draftCacheStore = draftCacheStore;
    }

    @Override
    public void saveDraft(Long postId, Long userId, String content, boolean isAutoSave) {
        delegate.saveDraft(postId, userId, content, isAutoSave);
        evictDraftCache(postId, userId, "save");
    }

    @Override
    public void deleteDraft(Long postId, Long userId) {
        delegate.deleteDraft(postId, userId);
        evictDraftCache(postId, userId, "delete");
    }

    @Override
    public long cleanExpiredDrafts(int expireDays) {
        return delegate.cleanExpiredDrafts(expireDays);
    }

    private void evictDraftCache(Long postId, Long userId, String operation) {
        try {
            draftCacheStore.evictDraft(postId, userId);
        } catch (Exception e) {
            log.warn("Failed to evict draft cache after {}: postId={}, userId={}, error={}",
                    operation, postId, userId, e.getMessage());
        }
    }
}
