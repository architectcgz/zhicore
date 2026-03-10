package com.zhicore.content.application.port.store;

import java.util.List;
import java.util.Set;

/**
 * 文章点赞缓存存储端口。
 *
 * 封装点赞标记与点赞数缓存操作，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface PostLikeStore {

    Boolean isLiked(Long userId, Long postId);

    Set<Long> findLikedPostIds(Long userId, List<Long> postIds);

    void markLiked(Long userId, Long postId);

    void unmarkLiked(Long userId, Long postId);

    void incrementLikeCount(Long postId);

    void decrementLikeCount(Long postId);

    Integer getLikeCount(Long postId);

    void cacheLikeCount(Long postId, int count);
}
