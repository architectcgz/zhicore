package com.zhicore.comment.application.port;

/**
 * 评论缓存 key 解析端口
 */
public interface CommentCacheKeyResolver {

    /**
     * 评论详情回源锁 key
     */
    String lockDetail(Long commentId);
}
