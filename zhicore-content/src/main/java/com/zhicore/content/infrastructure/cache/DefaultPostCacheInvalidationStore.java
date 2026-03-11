package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.content.application.port.cachekey.PostCacheKeyResolver;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 默认文章缓存失效实现。
 */
@Component
@RequiredArgsConstructor
public class DefaultPostCacheInvalidationStore implements PostCacheInvalidationStore {

    private final CacheStore cacheStore;
    private final PostCacheKeyResolver postCacheKeyResolver;

    @Override
    public void evictDetail(PostId postId) {
        cacheStore.delete(postCacheKeyResolver.detail(postId));
    }

    @Override
    public void evictContent(PostId postId) {
        cacheStore.delete(postCacheKeyResolver.content(postId));
    }

    @Override
    public void evictStats(PostId postId) {
        cacheStore.delete(
                postCacheKeyResolver.viewCount(postId),
                postCacheKeyResolver.likeCount(postId),
                postCacheKeyResolver.commentCount(postId),
                postCacheKeyResolver.favoriteCount(postId)
        );
    }

    @Override
    public void evictLatestList() {
        cacheStore.deletePattern(postCacheKeyResolver.listLatestPattern());
    }

    @Override
    public void evictAuthorLists(UserId authorId) {
        cacheStore.deletePattern(postCacheKeyResolver.listAuthorPattern(authorId));
    }

    @Override
    public void evictTagLists(Set<TagId> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        tagIds.forEach(tagId -> cacheStore.deletePattern(postCacheKeyResolver.listTagPattern(tagId)));
    }

    @Override
    public void evictAllRelated(PostId postId) {
        cacheStore.deletePattern(postCacheKeyResolver.allRelatedPattern(postId));
    }
}
