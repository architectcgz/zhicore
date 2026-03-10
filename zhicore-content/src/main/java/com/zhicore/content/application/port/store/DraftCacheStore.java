package com.zhicore.content.application.port.store;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.domain.valueobject.DraftSnapshot;

import java.time.Duration;
import java.util.List;

/**
 * 草稿缓存存储端口。
 *
 * 封装单草稿与草稿列表的三态缓存读写和失效逻辑，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface DraftCacheStore {

    CacheResult<DraftSnapshot> getLatestDraft(Long postId, Long userId);

    void setLatestDraft(Long postId, Long userId, DraftSnapshot draft, Duration ttl);

    void setLatestDraftNull(Long postId, Long userId, Duration ttl);

    CacheResult<List<DraftSnapshot>> getUserDrafts(Long userId);

    void setUserDrafts(Long userId, List<DraftSnapshot> drafts, Duration ttl);

    void setUserDraftsEmpty(Long userId, Duration ttl);

    void evictDraft(Long postId, Long userId);
}
