package com.zhicore.content.application.port.store;

import java.util.List;
import java.util.Set;

/**
 * 文章收藏缓存存储端口。
 *
 * 封装收藏标记与收藏数缓存操作，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface PostFavoriteStore {

    Boolean isFavorited(Long userId, Long postId);

    Set<Long> findFavoritedPostIds(Long userId, List<Long> postIds);

    void markFavorited(Long userId, Long postId);

    void unmarkFavorited(Long userId, Long postId);

    void incrementFavoriteCount(Long postId);

    void decrementFavoriteCount(Long postId);

    Integer getFavoriteCount(Long postId);

    void cacheFavoriteCount(Long postId, int count);
}
