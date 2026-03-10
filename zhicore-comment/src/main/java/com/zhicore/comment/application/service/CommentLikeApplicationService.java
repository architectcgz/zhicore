package com.zhicore.comment.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.comment.application.port.store.CommentLikeStore;
import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.comment.application.sentinel.CommentSentinelHandlers;
import com.zhicore.comment.application.sentinel.CommentSentinelResources;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评论点赞应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentLikeApplicationService {

    private final CommentLikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final CommentStatsRepository commentStatsRepository;
    private final CommentLikeStore commentLikeStore;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * 点赞评论
     *
     * 流程：同一事务中执行 INSERT ... ON CONFLICT DO NOTHING + 根据 affected_rows 更新 stats
     * Redis 缓存更新放在事务提交后，失败不影响数据正确性
     *
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    public void likeComment(Long userId, Long commentId) {
        // 检查评论是否存在
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "评论已删除，无法点赞");
        }

        // 数据库操作在事务中执行：利用唯一约束做幂等
        boolean[] actuallyInserted = {false};
        transactionTemplate.executeWithoutResult(status -> {
            // INSERT ... ON CONFLICT DO NOTHING，返回是否实际插入
            actuallyInserted[0] = likeRepository.insertIfAbsent(commentId, userId);

            // 仅当实际插入时才更新计数，避免并发重复点赞导致计数多加
            if (actuallyInserted[0]) {
                commentStatsRepository.incrementLikeCount(commentId);
            }
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            if (actuallyInserted[0]) {
                commentLikeStore.incrementLikeCount(commentId);
            }
            commentLikeStore.markLiked(userId, commentId);
        } catch (Exception e) {
            handleCacheUpdateFailure("like", commentId, userId, e);
        }

        log.info("Comment liked: commentId={}, userId={}, actuallyInserted={}",
                commentId, userId, actuallyInserted[0]);
    }

    /**
     * 取消点赞评论
     *
     * 流程：同一事务中执行 DELETE + 根据 affected_rows 更新 stats
     * Redis 缓存更新放在事务提交后，失败不影响数据正确性
     *
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    public void unlikeComment(Long userId, Long commentId) {
        // 数据库操作在事务中执行：利用 affected_rows 判断是否实际删除
        boolean[] actuallyDeleted = {false};
        transactionTemplate.executeWithoutResult(status -> {
            actuallyDeleted[0] = likeRepository.deleteAndReturnAffected(commentId, userId);

            // 仅当实际删除时才递减计数，使用 GREATEST 防止负数
            if (actuallyDeleted[0]) {
                commentStatsRepository.decrementLikeCount(commentId);
            }
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            if (actuallyDeleted[0]) {
                commentLikeStore.decrementLikeCount(commentId);
            }
            commentLikeStore.unmarkLiked(userId, commentId);
        } catch (Exception e) {
            handleCacheUpdateFailure("unlike", commentId, userId, e);
        }

        log.info("Comment unliked: commentId={}, userId={}, actuallyDeleted={}",
                commentId, userId, actuallyDeleted[0]);
    }

    /**
     * 检查是否已点赞
     *
     * @param userId 用户ID
     * @param commentId 评论ID
     * @return 是否已点赞
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

        // Redis 未命中，查数据库
        boolean exists = likeRepository.exists(commentId, userId);
        if (exists) {
            // 回填 Redis
            commentLikeStore.markLiked(userId, commentId);
        }
        return exists;
    }

    /**
     * 批量检查点赞状态（使用 Redis Pipeline 优化）
     *
     * @param userId 用户ID
     * @param commentIds 评论ID列表
     * @return 点赞状态映射
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

        // 对于 Redis 未命中的，查数据库
        if (!missedIds.isEmpty()) {
            List<Long> likedCommentIds = likeRepository.findLikedCommentIds(userId, missedIds);
            for (Long commentId : likedCommentIds) {
                likedMap.put(commentId, true);
                // 回填 Redis
                commentLikeStore.markLiked(userId, commentId);
            }
        }

        return likedMap;
    }

    /**
     * 获取评论点赞数
     *
     * @param commentId 评论ID
     * @return 点赞数
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

        // Redis 未命中，查数据库
        int dbCount = likeRepository.countByCommentId(commentId);
        // 回填 Redis
        commentLikeStore.cacheLikeCount(commentId, dbCount);
        return dbCount;
    }

    /**
     * 缓存更新失败处理
     */
    private void handleCacheUpdateFailure(String operation, Long commentId, Long userId, Exception e) {
        // 1. 记录失败日志
        log.warn("Redis 更新失败: operation={}, commentId={}, userId={}, error={}",
                operation, commentId, userId, e.getMessage());

        // 2. 记录指标（用于告警）
        meterRegistry.counter("cache.update.failure",
                "operation", operation,
                "service", "comment-service"
        ).increment();

        // 注意：不抛出异常，主流程已成功
        // CDC 和定时任务会自动修复数据
    }
}
