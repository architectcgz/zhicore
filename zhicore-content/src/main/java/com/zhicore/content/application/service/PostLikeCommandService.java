package com.zhicore.content.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostLikeStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostLike;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.repository.PostLikeRepository;
import com.zhicore.integration.messaging.post.PostLikedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostUnlikedIntegrationEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * 文章点赞写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeCommandService {

    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final PostLikeStore postLikeStore;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public void likePost(Long userId, Long postId) {
        if (Boolean.TRUE.equals(postLikeStore.isLiked(userId, postId))) {
            throw new BusinessException("已经点赞过了");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BusinessException("文章未发布，无法点赞");
        }

        Long authorId = post.getOwnerId().getValue();
        transactionTemplate.executeWithoutResult(status -> {
            if (likeRepository.exists(postId, userId)) {
                throw new BusinessException("已经点赞过了");
            }

            ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
            if (!idResponse.isSuccess() || idResponse.getData() == null) {
                log.error("Failed to generate like ID: {}", idResponse.getMessage());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成点赞ID失败");
            }
            likeRepository.save(new PostLike(idResponse.getData(), postId, userId));
        });

        try {
            postLikeStore.incrementLikeCount(postId);
            postLikeStore.markLiked(userId, postId);
        } catch (Exception e) {
            handleCacheUpdateFailure("like", postId, userId, e);
        }

        integrationEventPublisher.publish(new PostLikedIntegrationEvent(
                newEventId(),
                java.time.Instant.now(),
                post.getVersion(),
                postId,
                userId,
                authorId,
                post.getPublishedAt() == null ? null :
                        post.getPublishedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
        ));
        log.info("Post liked: postId={}, userId={}", postId, userId);
    }

    public void unlikePost(Long userId, Long postId) {
        if (!Boolean.TRUE.equals(postLikeStore.isLiked(userId, postId)) && !likeRepository.exists(postId, userId)) {
            throw new BusinessException("尚未点赞");
        }

        transactionTemplate.executeWithoutResult(status -> likeRepository.delete(postId, userId));

        try {
            postLikeStore.decrementLikeCount(postId);
            postLikeStore.unmarkLiked(userId, postId);
        } catch (Exception e) {
            handleCacheUpdateFailure("unlike", postId, userId, e);
        }

        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(0L);
        integrationEventPublisher.publish(new PostUnlikedIntegrationEvent(
                newEventId(),
                java.time.Instant.now(),
                aggregateVersion,
                postId,
                userId,
                null
        ));
        log.info("Post unliked: postId={}, userId={}", postId, userId);
    }

    private void handleCacheUpdateFailure(String operation, Long postId, Long userId, Exception e) {
        log.warn("Redis 更新失败: operation={}, postId={}, userId={}, error={}",
                operation, postId, userId, e.getMessage());
        meterRegistry.counter("cache.update.failure",
                "operation", operation,
                "service", "post-service"
        ).increment();
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
