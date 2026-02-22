package com.zhicore.content.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.event.post.PostLikedEvent;
import com.zhicore.api.event.post.PostUnlikedEvent;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostLike;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.repository.PostLikeRepository;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import com.zhicore.content.infrastructure.mq.PostEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章点赞应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeApplicationService {

    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * 点赞文章
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    public void likePost(Long userId, Long postId) {
        // 检查是否已点赞（先查 Redis）
        String likeKey = PostRedisKeys.userLiked(userId, postId);
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);

        if (Boolean.TRUE.equals(alreadyLiked)) {
            throw new BusinessException("已经点赞过了");
        }

        // 检查文章是否存在
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("文章未发布，无法点赞");
        }

        Long authorId = post.getOwnerId();

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            // 再次检查数据库（防止并发）
            if (likeRepository.exists(postId, userId)) {
                throw new BusinessException("已经点赞过了");
            }
            
            ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
            if (!idResponse.isSuccess() || idResponse.getData() == null) {
                log.error("Failed to generate like ID: {}", idResponse.getMessage());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成点赞ID失败");
            }
            Long likeId = idResponse.getData();
            PostLike like = new PostLike(likeId, postId, userId);
            likeRepository.save(like);
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(PostRedisKeys.likeCount(postId));
            redisTemplate.opsForValue().set(likeKey, "1");
        } catch (Exception e) {
            // Redis 更新失败处理
            handleCacheUpdateFailure("like", postId, userId, e);
        }

        // 发布事件（用于通知、排行榜更新）
        eventPublisher.publish(new PostLikedEvent(postId, userId, authorId));

        log.info("Post liked: postId={}, userId={}", postId, userId);
    }

    /**
     * 取消点赞
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    public void unlikePost(Long userId, Long postId) {
        // 检查是否已点赞
        String likeKey = PostRedisKeys.userLiked(userId, postId);
        Boolean liked = redisTemplate.hasKey(likeKey);

        if (!Boolean.TRUE.equals(liked)) {
            // 再查数据库确认
            if (!likeRepository.exists(postId, userId)) {
                throw new BusinessException("尚未点赞");
            }
        }

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            likeRepository.delete(postId, userId);
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().decrement(PostRedisKeys.likeCount(postId));
            redisTemplate.delete(likeKey);
        } catch (Exception e) {
            // Redis 更新失败处理
            handleCacheUpdateFailure("unlike", postId, userId, e);
        }

        // 发布事件
        eventPublisher.publish(new PostUnlikedEvent(postId, userId));

        log.info("Post unliked: postId={}, userId={}", postId, userId);
    }

    /**
     * 检查是否已点赞
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 是否已点赞
     */
    public boolean isLiked(Long userId, Long postId) {
        String likeKey = PostRedisKeys.userLiked(userId, postId);
        Boolean liked = redisTemplate.hasKey(likeKey);
        
        if (Boolean.TRUE.equals(liked)) {
            return true;
        }
        
        // Redis 未命中，查数据库
        boolean exists = likeRepository.exists(postId, userId);
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
     * @param postIds 文章ID列表
     * @return 点赞状态映射
     */
    public Map<Long, Boolean> batchCheckLiked(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 使用 Pipeline 批量查询 Redis
        List<Object> results = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Long postId : postIds) {
                String key = PostRedisKeys.userLiked(userId, postId);
                connection.keyCommands().exists(key.getBytes());
            }
            return null;
        });

        Map<Long, Boolean> likedMap = new HashMap<>();
        List<Long> missedIds = new java.util.ArrayList<>();

        for (int i = 0; i < postIds.size(); i++) {
            Long postId = postIds.get(i);
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result) || (result instanceof Long && (Long) result > 0)) {
                likedMap.put(postId, true);
            } else {
                likedMap.put(postId, false);
                missedIds.add(postId);
            }
        }

        // 对于 Redis 未命中的，查数据库
        if (!missedIds.isEmpty()) {
            List<Long> likedPostIds = likeRepository.findLikedPostIds(userId, missedIds);
            for (Long postId : likedPostIds) {
                likedMap.put(postId, true);
                // 回填 Redis
                redisTemplate.opsForValue().set(PostRedisKeys.userLiked(userId, postId), "1");
            }
        }

        return likedMap;
    }

    /**
     * 获取文章点赞数
     *
     * @param postId 文章ID
     * @return 点赞数
     */
    public int getLikeCount(Long postId) {
        String key = PostRedisKeys.likeCount(postId);
        Object count = redisTemplate.opsForValue().get(key);
        
        if (count != null) {
            return Integer.parseInt(count.toString());
        }
        
        // Redis 未命中，查数据库
        int dbCount = likeRepository.countByPostId(postId);
        // 回填 Redis
        redisTemplate.opsForValue().set(key, dbCount);
        return dbCount;
    }

    /**
     * 缓存更新失败处理
     */
    private void handleCacheUpdateFailure(String operation, Long postId, Long userId, Exception e) {
        // 1. 记录失败日志
        log.warn("Redis 更新失败: operation={}, postId={}, userId={}, error={}",
                operation, postId, userId, e.getMessage());

        // 2. 记录指标（用于告警）
        meterRegistry.counter("cache.update.failure",
                "operation", operation,
                "service", "post-service"
        ).increment();

        // 注意：不抛出异常，主流程已成功
        // CDC 和定时任务会自动修复数据
    }
}
