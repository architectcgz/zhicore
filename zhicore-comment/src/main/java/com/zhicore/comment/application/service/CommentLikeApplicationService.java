package com.zhicore.comment.application.service;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentLike;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import com.zhicore.comment.domain.repository.CommentRepository;
import com.zhicore.comment.infrastructure.cache.CommentRedisKeys;
import com.zhicore.comment.infrastructure.repository.mapper.CommentStatsMapper;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final CommentStatsMapper statsMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * 点赞评论
     *
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     *
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    public void likeComment(Long userId, Long commentId) {
        // 检查是否已点赞（先查 Redis）
        String likeKey = CommentRedisKeys.userLiked(userId, commentId);
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);

        if (Boolean.TRUE.equals(alreadyLiked)) {
            throw new BusinessException("已经点赞过了");
        }

        // 检查评论是否存在
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "评论不存在"));

        if (comment.isDeleted()) {
            throw new BusinessException("评论已删除，无法点赞");
        }

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            // 再次检查数据库（防止并发）
            if (likeRepository.exists(commentId, userId)) {
                throw new BusinessException("已经点赞过了");
            }

            // CommentLike uses composite key (commentId, userId), no separate id needed
            CommentLike like = new CommentLike(commentId, userId);
            likeRepository.save(like);

            // 更新统计表
            statsMapper.incrementLikeCount(commentId);
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(CommentRedisKeys.likeCount(commentId));
            redisTemplate.opsForValue().set(likeKey, "1");
        } catch (Exception e) {
            handleCacheUpdateFailure("like", commentId, userId, e);
        }

        log.info("Comment liked: commentId={}, userId={}", commentId, userId);
    }

    /**
     * 取消点赞评论
     *
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     *
     * @param userId 用户ID
     * @param commentId 评论ID
     */
    public void unlikeComment(Long userId, Long commentId) {
        // 检查是否已点赞
        String likeKey = CommentRedisKeys.userLiked(userId, commentId);
        Boolean liked = redisTemplate.hasKey(likeKey);

        if (!Boolean.TRUE.equals(liked)) {
            // 再查数据库确认
            if (!likeRepository.exists(commentId, userId)) {
                throw new BusinessException("尚未点赞");
            }
        }

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            likeRepository.delete(commentId, userId);

            // 更新统计表
            statsMapper.decrementLikeCount(commentId);
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().decrement(CommentRedisKeys.likeCount(commentId));
            redisTemplate.delete(likeKey);
        } catch (Exception e) {
            handleCacheUpdateFailure("unlike", commentId, userId, e);
        }

        log.info("Comment unliked: commentId={}, userId={}", commentId, userId);
    }

    /**
     * 检查是否已点赞
     *
     * @param userId 用户ID
     * @param commentId 评论ID
     * @return 是否已点赞
     */
    public boolean isLiked(Long userId, Long commentId) {
        String likeKey = CommentRedisKeys.userLiked(userId, commentId);
        Boolean liked = redisTemplate.hasKey(likeKey);

        if (Boolean.TRUE.equals(liked)) {
            return true;
        }

        // Redis 未命中，查数据库
        boolean exists = likeRepository.exists(commentId, userId);
        if (exists) {
            // 回填 Redis
            redisTemplate.opsForValue().set(likeKey, "1");
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
    public Map<Long, Boolean> batchCheckLiked(Long userId, List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 使用 Pipeline 批量查询 Redis
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long commentId : commentIds) {
                String key = CommentRedisKeys.userLiked(userId, commentId);
                connection.keyCommands().exists(key.getBytes());
            }
            return null;
        });

        Map<Long, Boolean> likedMap = new HashMap<>();
        List<Long> missedIds = new java.util.ArrayList<>();

        for (int i = 0; i < commentIds.size(); i++) {
            Long commentId = commentIds.get(i);
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result) || (result instanceof Long && (Long) result > 0)) {
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
                redisTemplate.opsForValue().set(CommentRedisKeys.userLiked(userId, commentId), "1");
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
    public int getLikeCount(Long commentId) {
        String key = CommentRedisKeys.likeCount(commentId);
        Object count = redisTemplate.opsForValue().get(key);

        if (count != null) {
            return Integer.parseInt(count.toString());
        }

        // Redis 未命中，查数据库
        int dbCount = likeRepository.countByCommentId(commentId);
        // 回填 Redis
        redisTemplate.opsForValue().set(key, dbCount);
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
