package com.zhicore.content.application.port.cachekey;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;

/**
 * 文章缓存 key 解析端口。
 */
public interface PostCacheKeyResolver {

    String detail(PostId postId);

    String content(PostId postId);

    String lockDetail(PostId postId);

    String listLatest(int pageNumber, int size);

    String listAuthor(UserId authorId, int pageNumber, int size);

    String listTag(TagId tagId, int pageNumber, int size);

    String listLatestPattern();

    String listAuthorPattern(UserId authorId);

    String listTagPattern(TagId tagId);

    String allRelatedPattern(PostId postId);

    String viewCount(PostId postId);

    String likeCount(PostId postId);

    String commentCount(PostId postId);

    String favoriteCount(PostId postId);
}
