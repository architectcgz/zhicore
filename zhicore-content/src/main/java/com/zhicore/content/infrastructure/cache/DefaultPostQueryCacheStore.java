package com.zhicore.content.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.content.application.port.cachekey.PostCacheKeyResolver;
import com.zhicore.content.application.port.store.PostQueryCacheStore;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.query.view.PostListItemView;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 默认文章查询缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class DefaultPostQueryCacheStore implements PostQueryCacheStore {

    private static final TypeReference<List<PostListItemView>> POST_LIST_TYPE = new TypeReference<>() {};

    private final CacheStore cacheStore;
    private final PostCacheKeyResolver postCacheKeyResolver;

    @Override
    public CacheResult<PostDetailView> getDetail(PostId postId) {
        return cacheStore.get(postCacheKeyResolver.detail(postId), PostDetailView.class);
    }

    @Override
    public void setDetail(PostId postId, PostDetailView detailView, Duration ttl) {
        cacheStore.set(postCacheKeyResolver.detail(postId), detailView, ttl);
    }

    @Override
    public void setDetailNull(PostId postId, Duration ttl) {
        cacheStore.setIfAbsent(postCacheKeyResolver.detail(postId), null, ttl);
    }

    @Override
    public CacheResult<List<PostListItemView>> getLatestPosts(int pageNumber, int pageSize) {
        return cacheStore.get(postCacheKeyResolver.listLatest(pageNumber, pageSize), POST_LIST_TYPE);
    }

    @Override
    public void setLatestPosts(int pageNumber, int pageSize, List<PostListItemView> posts, Duration ttl) {
        cacheStore.set(postCacheKeyResolver.listLatest(pageNumber, pageSize), posts, ttl);
    }

    @Override
    public CacheResult<List<PostListItemView>> getPostsByAuthor(UserId authorId, int pageNumber, int pageSize) {
        return cacheStore.get(postCacheKeyResolver.listAuthor(authorId, pageNumber, pageSize), POST_LIST_TYPE);
    }

    @Override
    public void setPostsByAuthor(UserId authorId, int pageNumber, int pageSize, List<PostListItemView> posts, Duration ttl) {
        cacheStore.set(postCacheKeyResolver.listAuthor(authorId, pageNumber, pageSize), posts, ttl);
    }

    @Override
    public CacheResult<List<PostListItemView>> getPostsByTag(TagId tagId, int pageNumber, int pageSize) {
        return cacheStore.get(postCacheKeyResolver.listTag(tagId, pageNumber, pageSize), POST_LIST_TYPE);
    }

    @Override
    public void setPostsByTag(TagId tagId, int pageNumber, int pageSize, List<PostListItemView> posts, Duration ttl) {
        cacheStore.set(postCacheKeyResolver.listTag(tagId, pageNumber, pageSize), posts, ttl);
    }
}
