package com.zhicore.comment.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.comment.application.port.store.CommentLikeStore;
import com.zhicore.comment.application.sentinel.CommentSentinelHandlers;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评论点赞读服务。
 */
@Service
@RequiredArgsConstructor
public class CommentLikeQueryService {

    private final CommentLikeRepository likeRepository;
    private final CommentLikeStore commentLikeStore;

    /**
     * 检查是否已点赞。
     */
    @SentinelResource(
            value = CommentSentinelResources.IS_COMMENT_LIKED,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleIsCommentLikedBlocked"
    )
    public boolean isLiked(Long userId, Long commentId) {
        if (Boolean.TRUE.equals(commentLikeStore.isLiked(userId, commentId))) {
            return true;
        }

        boolean exists = likeRepository.exists(commentId, userId);
        if (exists) {
            commentLikeStore.markLiked(userId, commentId);
        }
        return exists;
    }

    /**
     * 批量检查点赞状态。
     */
    @SentinelResource(
            value = CommentSentinelResources.BATCH_CHECK_COMMENT_LIKED,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleBatchCheckCommentLikedBlocked"
    )
    public Map<Long, Boolean> batchCheckLiked(Long userId, List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Boolean> likedMap = new HashMap<>();
        List<Long> missedIds = new java.util.ArrayList<>();
        Set<Long> cachedLikedIds = commentLikeStore.findLikedCommentIds(userId, commentIds);

        for (Long commentId : commentIds) {
            if (cachedLikedIds.contains(commentId)) {
                likedMap.put(commentId, true);
            } else {
                likedMap.put(commentId, false);
                missedIds.add(commentId);
            }
        }

        if (!missedIds.isEmpty()) {
            List<Long> likedCommentIds = likeRepository.findLikedCommentIds(userId, missedIds);
            for (Long commentId : likedCommentIds) {
                likedMap.put(commentId, true);
                commentLikeStore.markLiked(userId, commentId);
            }
        }

        return likedMap;
    }

    /**
     * 获取评论点赞数。
     */
    @SentinelResource(
            value = CommentSentinelResources.GET_COMMENT_LIKE_COUNT,
            blockHandlerClass = CommentSentinelHandlers.class,
            blockHandler = "handleGetCommentLikeCountBlocked"
    )
    public int getLikeCount(Long commentId) {
        Integer count = commentLikeStore.getLikeCount(commentId);
        if (count != null) {
            return count;
        }

        int dbCount = likeRepository.countByCommentId(commentId);
        commentLikeStore.cacheLikeCount(commentId, dbCount);
        return dbCount;
    }
}
