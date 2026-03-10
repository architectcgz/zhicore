package com.zhicore.comment.application.port.store;

import java.util.List;
import java.util.Set;

/**
 * 评论点赞缓存存储端口。
 *
 * 封装点赞标记与点赞数缓存，避免应用层直接依赖 RedisTemplate。
 */
public interface CommentLikeStore {

    void incrementLikeCount(Long commentId);

    void decrementLikeCount(Long commentId);

    Integer getLikeCount(Long commentId);

    void cacheLikeCount(Long commentId, int count);

    Boolean isLiked(Long userId, Long commentId);

    Set<Long> findLikedCommentIds(Long userId, List<Long> commentIds);

    void markLiked(Long userId, Long commentId);

    void unmarkLiked(Long userId, Long commentId);
}
