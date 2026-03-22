package com.zhicore.comment.infrastructure.cache;

import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.port.CommentCacheKeyResolver;
import org.springframework.stereotype.Component;

/**
 * 默认评论缓存 key 解析实现
 */
@Component
public class DefaultCommentCacheKeyResolver implements CommentCacheKeyResolver {

    @Override
    public String lockDetail(Long commentId) {
        return CommentRedisKeys.lockDetail(commentId);
    }

    @Override
    public String lockHomepageSnapshot(Long postId, CommentSortType sortType, int size, int hotRepliesLimit) {
        return CommentRedisKeys.lockHomepageSnapshot(postId, sortType, size, hotRepliesLimit);
    }
}
