package com.zhicore.content.application.port.store;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;

import java.util.Set;

/**
 * 文章缓存失效端口。
 */
public interface PostCacheInvalidationStore {

    void evictDetail(PostId postId);

    void evictContent(PostId postId);

    void evictStats(PostId postId);

    void evictLatestList();

    void evictAuthorLists(UserId authorId);

    void evictTagLists(Set<TagId> tagIds);

    void evictAllRelated(PostId postId);
}
