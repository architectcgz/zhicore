package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cachekey.PostCacheKeyResolver;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * 默认文章缓存 key 解析实现。
 */
@Component
public class DefaultPostCacheKeyResolver implements PostCacheKeyResolver {

    @Override
    public String detail(PostId postId) {
        return PostRedisKeys.detail(postId);
    }

    @Override
    public String content(PostId postId) {
        return PostRedisKeys.content(postId);
    }

    @Override
    public String lockDetail(PostId postId) {
        return PostRedisKeys.lockDetail(postId);
    }

    @Override
    public String listLatest(int pageNumber, int size) {
        return PostRedisKeys.listLatest(pageNumber, size);
    }

    @Override
    public String listAuthor(UserId authorId, int pageNumber, int size) {
        return PostRedisKeys.listAuthor(authorId, pageNumber, size);
    }

    @Override
    public String listTag(TagId tagId, int pageNumber, int size) {
        return PostRedisKeys.listTag(tagId, pageNumber, size);
    }

    @Override
    public String listLatestPattern() {
        return PostRedisKeys.listLatestPattern();
    }

    @Override
    public String listAuthorPattern(UserId authorId) {
        return PostRedisKeys.listAuthorPattern(authorId);
    }

    @Override
    public String listTagPattern(TagId tagId) {
        return PostRedisKeys.listTagPattern(tagId);
    }

    @Override
    public String allRelatedPattern(PostId postId) {
        return PostRedisKeys.allRelatedPattern(postId);
    }

    @Override
    public String viewCount(PostId postId) {
        return PostRedisKeys.viewCount(postId);
    }

    @Override
    public String likeCount(PostId postId) {
        return PostRedisKeys.likeCount(postId);
    }

    @Override
    public String commentCount(PostId postId) {
        return PostRedisKeys.commentCount(postId);
    }

    @Override
    public String favoriteCount(PostId postId) {
        return PostRedisKeys.favoriteCount(postId);
    }
}
