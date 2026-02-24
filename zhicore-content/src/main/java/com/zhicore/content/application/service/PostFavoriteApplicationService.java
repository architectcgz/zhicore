package com.zhicore.content.application.service;

import com.zhicore.clients.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostFavorite;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import com.zhicore.integration.messaging.post.PostFavoritedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostUnfavoritedIntegrationEvent;
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
import java.util.UUID;

/**
 * 文章收藏应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostFavoriteApplicationService {

    private final PostFavoriteRepository favoriteRepository;
    private final PostRepository postRepository;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * 收藏文章
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    public void favoritePost(Long userId, Long postId) {
        // 检查是否已收藏（先查 Redis）
        String favoriteKey = PostRedisKeys.userFavorited(userId, postId);
        Boolean alreadyFavorited = redisTemplate.hasKey(favoriteKey);

        if (Boolean.TRUE.equals(alreadyFavorited)) {
            throw new BusinessException("已经收藏过了");
        }

        // 检查文章是否存在
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("文章未发布，无法收藏");
        }

        Long authorId = post.getOwnerId().getValue();

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            // 再次检查数据库（防止并发）
            if (favoriteRepository.exists(postId, userId)) {
                throw new BusinessException("已经收藏过了");
            }
            
            ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
            if (!idResponse.isSuccess() || idResponse.getData() == null) {
                log.error("Failed to generate favorite ID: {}", idResponse.getMessage());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成收藏ID失败");
            }
            Long favoriteId = idResponse.getData();
            PostFavorite favorite = new PostFavorite(favoriteId, postId, userId);
            favoriteRepository.save(favorite);
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().increment(PostRedisKeys.favoriteCount(postId));
            redisTemplate.opsForValue().set(favoriteKey, "1");
        } catch (Exception e) {
            handleCacheUpdateFailure("favorite", postId, userId, e);
        }

        // 发布事件（用于通知、排行榜更新）
        integrationEventPublisher.publish(new PostFavoritedIntegrationEvent(
                newEventId(),
                java.time.Instant.now(),
                null,
                postId,
                userId,
                authorId
        ));

        log.info("Post favorited: postId={}, userId={}", postId, userId);
    }


    /**
     * 取消收藏
     * 
     * 注意：Redis 操作放在事务提交后执行，避免事务回滚导致数据不一致
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    public void unfavoritePost(Long userId, Long postId) {
        // 检查是否已收藏
        String favoriteKey = PostRedisKeys.userFavorited(userId, postId);
        Boolean favorited = redisTemplate.hasKey(favoriteKey);

        if (!Boolean.TRUE.equals(favorited)) {
            // 再查数据库确认
            if (!favoriteRepository.exists(postId, userId)) {
                throw new BusinessException("尚未收藏");
            }
        }

        // 数据库操作在事务中执行
        transactionTemplate.executeWithoutResult(status -> {
            favoriteRepository.delete(postId, userId);
        });

        // 事务提交成功后，更新 Redis 缓存
        try {
            redisTemplate.opsForValue().decrement(PostRedisKeys.favoriteCount(postId));
            redisTemplate.delete(favoriteKey);
        } catch (Exception e) {
            handleCacheUpdateFailure("unfavorite", postId, userId, e);
        }

        // 发布事件
        integrationEventPublisher.publish(new PostUnfavoritedIntegrationEvent(
                newEventId(),
                java.time.Instant.now(),
                null,
                postId,
                userId
        ));

        log.info("Post unfavorited: postId={}, userId={}", postId, userId);
    }

    /**
     * 检查是否已收藏
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 是否已收藏
     */
    public boolean isFavorited(Long userId, Long postId) {
        String favoriteKey = PostRedisKeys.userFavorited(userId, postId);
        Boolean favorited = redisTemplate.hasKey(favoriteKey);
        
        if (Boolean.TRUE.equals(favorited)) {
            return true;
        }
        
        // Redis 未命中，查数据库
        boolean exists = favoriteRepository.exists(postId, userId);
        if (exists) {
            // 回填 Redis
            redisTemplate.opsForValue().set(favoriteKey, "1");
        }
        return exists;
    }

    /**
     * 批量检查收藏状态（使用 Redis Pipeline 优化）
     *
     * @param userId 用户ID
     * @param postIds 文章ID列表
     * @return 收藏状态映射
     */
    public Map<Long, Boolean> batchCheckFavorited(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 使用 Pipeline 批量查询 Redis
        List<Object> results = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Long postId : postIds) {
                String key = PostRedisKeys.userFavorited(userId, postId);
                connection.keyCommands().exists(key.getBytes());
            }
            return null;
        });

        Map<Long, Boolean> favoritedMap = new HashMap<>();
        List<Long> missedIds = new java.util.ArrayList<>();

        for (int i = 0; i < postIds.size(); i++) {
            Long postId = postIds.get(i);
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result) || (result instanceof Long && (Long) result > 0)) {
                favoritedMap.put(postId, true);
            } else {
                favoritedMap.put(postId, false);
                missedIds.add(postId);
            }
        }

        // 对于 Redis 未命中的，查数据库
        if (!missedIds.isEmpty()) {
            List<Long> favoritedPostIds = favoriteRepository.findFavoritedPostIds(userId, missedIds);
            for (Long postId : favoritedPostIds) {
                favoritedMap.put(postId, true);
                // 回填 Redis
                redisTemplate.opsForValue().set(PostRedisKeys.userFavorited(userId, postId), "1");
            }
        }

        return favoritedMap;
    }

    /**
     * 获取文章收藏数
     *
     * @param postId 文章ID
     * @return 收藏数
     */
    public int getFavoriteCount(Long postId) {
        String key = PostRedisKeys.favoriteCount(postId);
        Object count = redisTemplate.opsForValue().get(key);
        
        if (count != null) {
            return Integer.parseInt(count.toString());
        }
        
        // Redis 未命中，查数据库
        int dbCount = favoriteRepository.countByPostId(postId);
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

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
