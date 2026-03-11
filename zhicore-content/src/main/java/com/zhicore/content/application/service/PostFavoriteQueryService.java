package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.content.application.port.store.PostFavoriteStore;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章收藏查询服务。
 */
@Service
@RequiredArgsConstructor
public class PostFavoriteQueryService {

    private final PostFavoriteRepository favoriteRepository;
    private final PostFavoriteStore postFavoriteStore;

    @SentinelResource(
            value = ContentSentinelResources.IS_POST_FAVORITED,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleIsPostFavoritedBlocked"
    )
    public boolean isFavorited(Long userId, Long postId) {
        if (Boolean.TRUE.equals(postFavoriteStore.isFavorited(userId, postId))) {
            return true;
        }

        boolean exists = favoriteRepository.exists(postId, userId);
        if (exists) {
            postFavoriteStore.markFavorited(userId, postId);
        }
        return exists;
    }

    @SentinelResource(
            value = ContentSentinelResources.BATCH_CHECK_POST_FAVORITED,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleBatchCheckPostFavoritedBlocked"
    )
    public Map<Long, Boolean> batchCheckFavorited(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Boolean> favoritedMap = new HashMap<>();
        List<Long> missedIds = new java.util.ArrayList<>();
        Set<Long> favoritedPostIdsInCache = postFavoriteStore.findFavoritedPostIds(userId, postIds);

        for (Long postId : postIds) {
            if (favoritedPostIdsInCache.contains(postId)) {
                favoritedMap.put(postId, true);
            } else {
                favoritedMap.put(postId, false);
                missedIds.add(postId);
            }
        }

        if (!missedIds.isEmpty()) {
            List<Long> favoritedPostIds = favoriteRepository.findFavoritedPostIds(userId, missedIds);
            for (Long postId : favoritedPostIds) {
                favoritedMap.put(postId, true);
                postFavoriteStore.markFavorited(userId, postId);
            }
        }
        return favoritedMap;
    }

    @SentinelResource(
            value = ContentSentinelResources.GET_POST_FAVORITE_COUNT,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostFavoriteCountBlocked"
    )
    public int getFavoriteCount(Long postId) {
        Integer count = postFavoriteStore.getFavoriteCount(postId);
        if (count != null) {
            return count;
        }

        int dbCount = favoriteRepository.countByPostId(postId);
        postFavoriteStore.cacheFavoriteCount(postId, dbCount);
        return dbCount;
    }
}
