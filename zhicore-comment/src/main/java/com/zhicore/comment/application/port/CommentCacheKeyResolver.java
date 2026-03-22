package com.zhicore.comment.application.port;

import com.zhicore.comment.application.dto.CommentSortType;

/**
 * 评论缓存 key 解析端口
 */
public interface CommentCacheKeyResolver {

    /**
     * 评论详情回源锁 key
     */
    String lockDetail(Long commentId);

    /**
     * 首页评论快照回源锁 key
     */
    String lockHomepageSnapshot(Long postId, CommentSortType sortType, int size, int hotRepliesLimit);
}
