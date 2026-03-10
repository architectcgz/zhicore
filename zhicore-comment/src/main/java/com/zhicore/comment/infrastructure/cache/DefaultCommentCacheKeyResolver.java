package com.zhicore.comment.infrastructure.cache;

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
}
