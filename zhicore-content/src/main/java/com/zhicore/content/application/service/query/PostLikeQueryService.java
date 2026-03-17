package com.zhicore.content.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.content.application.port.store.PostLikeStore;
import com.zhicore.content.application.sentinel.ContentSentinelHandlers;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import com.zhicore.content.domain.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章点赞查询服务。
 */
@Service
@RequiredArgsConstructor
public class PostLikeQueryService {

    private final PostLikeRepository likeRepository;
    private final PostLikeStore postLikeStore;

    @SentinelResource(
            value = ContentSentinelResources.IS_POST_LIKED,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleIsPostLikedBlocked"
    )
    public boolean isLiked(Long userId, Long postId) {
        if (Boolean.TRUE.equals(postLikeStore.isLiked(userId, postId))) {
            return true;
        }

        boolean exists = likeRepository.exists(postId, userId);
        if (exists) {
            postLikeStore.markLiked(userId, postId);
        }
        return exists;
    }

    @SentinelResource(
            value = ContentSentinelResources.BATCH_CHECK_POST_LIKED,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleBatchCheckPostLikedBlocked"
    )
    public Map<Long, Boolean> batchCheckLiked(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Boolean> likedMap = new HashMap<>();
        List<Long> missedIds = new java.util.ArrayList<>();
        Set<Long> cachedLikedIds = postLikeStore.findLikedPostIds(userId, postIds);

        for (Long postId : postIds) {
            if (cachedLikedIds.contains(postId)) {
                likedMap.put(postId, true);
            } else {
                likedMap.put(postId, false);
                missedIds.add(postId);
            }
        }

        if (!missedIds.isEmpty()) {
            List<Long> likedPostIds = likeRepository.findLikedPostIds(userId, missedIds);
            for (Long postId : likedPostIds) {
                likedMap.put(postId, true);
                postLikeStore.markLiked(userId, postId);
            }
        }
        return likedMap;
    }

    @SentinelResource(
            value = ContentSentinelResources.GET_POST_LIKE_COUNT,
            blockHandlerClass = ContentSentinelHandlers.class,
            blockHandler = "handleGetPostLikeCountBlocked"
    )
    public int getLikeCount(Long postId) {
        Integer count = postLikeStore.getLikeCount(postId);
        if (count != null) {
            return count;
        }

        int dbCount = likeRepository.countByPostId(postId);
        postLikeStore.cacheLikeCount(postId, dbCount);
        return dbCount;
    }
}
