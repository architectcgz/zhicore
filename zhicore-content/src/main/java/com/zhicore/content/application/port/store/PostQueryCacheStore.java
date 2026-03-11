package com.zhicore.content.application.port.store;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.query.view.PostListItemView;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;

import java.time.Duration;
import java.util.List;

/**
 * 文章查询缓存存储端口。
 *
 * 封装文章详情与列表查询缓存的三态读写逻辑，
 * 避免应用层直接依赖通用缓存实现和 key 解析细节。
 */
public interface PostQueryCacheStore {

    CacheResult<PostDetailView> getDetail(PostId postId);

    void setDetail(PostId postId, PostDetailView detailView, Duration ttl);

    void setDetailNull(PostId postId, Duration ttl);

    CacheResult<List<PostListItemView>> getLatestPosts(int pageNumber, int pageSize);

    void setLatestPosts(int pageNumber, int pageSize, List<PostListItemView> posts, Duration ttl);

    CacheResult<List<PostListItemView>> getPostsByAuthor(UserId authorId, int pageNumber, int pageSize);

    void setPostsByAuthor(UserId authorId, int pageNumber, int pageSize, List<PostListItemView> posts, Duration ttl);

    CacheResult<List<PostListItemView>> getPostsByTag(TagId tagId, int pageNumber, int pageSize);

    void setPostsByTag(TagId tagId, int pageNumber, int pageSize, List<PostListItemView> posts, Duration ttl);
}
